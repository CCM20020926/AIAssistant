"""Concurrency harness: fires N requests simultaneously, measures latency, and
decides pass/fail for one concurrency group. Failing a group raises
LoadTestFailed so the suite (and run_all) exits early.
"""
import asyncio
import math
import time
from dataclasses import dataclass
from typing import Awaitable, Callable, Dict, List, Optional

import aiohttp

from config import (
    Ctx, MAX_RETRY_ROUNDS, OVER_LIMIT_STATUS, RETRY_BACKOFF,
)


class LoadTestFailed(Exception):
    """Raised when a concurrency group does not meet its latency SLA."""


@dataclass
class Sample:
    ok: bool
    latency: float          # seconds, measured from the common batch start t0
    error: Optional[str] = None
    status: Optional[int] = None       # HTTP status of the final attempt
    over_limit: bool = False           # True when rejected with OVER_LIMIT_STATUS (429)


# task(session, ctx, index, t0) -> Sample
Task = Callable[[aiohttp.ClientSession, Ctx, int, float], Awaitable[Sample]]


def percentile(sorted_vals: List[float], p: float) -> float:
    if not sorted_vals:
        return 0.0
    if len(sorted_vals) == 1:
        return sorted_vals[0]
    k = (len(sorted_vals) - 1) * p
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return sorted_vals[int(k)]
    return sorted_vals[f] * (c - k) + sorted_vals[c] * (k - f)


async def run_group(
    suite: str,
    level: int,
    threshold: float,
    task: Task,
    ctx: Ctx,
    metric_label: str = "端到端时延",
) -> None:
    connector = aiohttp.TCPConnector(limit=0, enable_cleanup_closed=True, ttl_dns_cache=300)
    # sock_read must exceed the threshold so we measure real latency, not a client abort.
    timeout = aiohttp.ClientTimeout(
        total=None, connect=15, sock_connect=15, sock_read=threshold + 60
    )

    print(f"\n=== [{suite}] concurrency={level}  threshold<{threshold:g}s  metric={metric_label} ===")

    final: Dict[int, Sample] = {}
    over_limit_log: List[str] = []
    retried_indices: set = set()

    async with aiohttp.ClientSession(connector=connector, timeout=timeout, trust_env=False) as session:
        # t0 is fixed once; retried requests are fired later, so their latency
        # (finish - t0) naturally includes the 2s backoff, per the agreed rule.
        t0 = time.perf_counter()
        pending = list(range(level))

        for attempt in range(MAX_RETRY_ROUNDS + 1):
            raw = await asyncio.gather(
                *(task(session, ctx, i, t0) for i in pending),
                return_exceptions=True,
            )
            over_limit_now: List[int] = []
            for idx, r in zip(pending, raw):
                if isinstance(r, BaseException):
                    final[idx] = Sample(False, time.perf_counter() - t0,
                                        error=f"{type(r).__name__}: {r}")
                else:
                    final[idx] = r
                    if r.over_limit:
                        over_limit_now.append(idx)

            if not over_limit_now:
                break

            if attempt < MAX_RETRY_ROUNDS:
                retried_indices.update(over_limit_now)
                over_limit_log.append(
                    f"round {attempt + 1}: {len(over_limit_now)} over-limit "
                    f"(HTTP {OVER_LIMIT_STATUS}) -> retry in {RETRY_BACKOFF:g}s "
                    f"(indices: {_fmt_indices(over_limit_now)})"
                )
                print(f"  [retry] {over_limit_log[-1]}")
                await asyncio.sleep(RETRY_BACKOFF)
                pending = over_limit_now
            else:
                over_limit_log.append(
                    f"round {attempt + 1}: {len(over_limit_now)} still over-limit "
                    f"after {MAX_RETRY_ROUNDS} retries -> marked FAILED "
                    f"(indices: {_fmt_indices(over_limit_now)})"
                )
                print(f"  [retry] {over_limit_log[-1]}")

        wall = time.perf_counter() - t0

    samples = [final[i] for i in sorted(final)]
    ok = [s for s in samples if s.ok]
    failed = [s for s in samples if not s.ok]
    latencies = sorted(s.latency for s in ok)
    group_latency = max((s.latency for s in samples), default=wall)

    print(f"  requests : {level}   success: {len(ok)}   errors: {len(failed)}")
    if retried_indices:
        recovered = sum(1 for i in retried_indices if final[i].ok)
        print(f"  over-limit: {len(retried_indices)} distinct requests were retried "
              f"(recovered {recovered}, still-failed {len(retried_indices) - recovered})")
    print(f"  batch wall (all-done): {wall:.3f}s")
    if latencies:
        avg = sum(latencies) / len(latencies)
        print(
            f"  latency  min={latencies[0]:.3f}s  avg={avg:.3f}s  "
            f"p50={percentile(latencies, 0.50):.3f}s  "
            f"p95={percentile(latencies, 0.95):.3f}s  "
            f"p99={percentile(latencies, 0.99):.3f}s  "
            f"max={latencies[-1]:.3f}s"
        )
    print(f"  GROUP {metric_label}: {group_latency:.3f}s")

    passed = (len(failed) == 0) and (group_latency < threshold)

    if passed:
        print(f"  RESULT: PASS  (端到端时延 {group_latency:.3f}s < {threshold:g}s)")
        return

    print(f"  RESULT: FAILED")
    if failed:
        print(f"  first errors ({len(failed)} failed):")
        for s in failed[:5]:
            print(f"    - latency={s.latency:.3f}s  status={s.status}  error={s.error}")
    if group_latency >= threshold:
        print(f"  reason: group latency {group_latency:.3f}s >= threshold {threshold:g}s")
    raise LoadTestFailed(f"[{suite}] concurrency={level} failed")


def _fmt_indices(indices: List[int], limit: int = 20) -> str:
    shown = ", ".join(str(i) for i in indices[:limit])
    if len(indices) > limit:
        shown += f", ... (+{len(indices) - limit} more)"
    return shown
