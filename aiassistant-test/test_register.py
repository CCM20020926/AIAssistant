"""Register endpoint load test: 10 / 100 concurrent, latency < 10s to pass."""
import argparse
import asyncio
import time

from api import register_task
from config import (
    Ctx, DEFAULT_BASE_URL, PASSWORD, REGISTER_LEVELS, REGISTER_THRESHOLD,
)
from runner import LoadTestFailed, run_group


def _run_id() -> str:
    return str(int(time.time()))[-6:]


async def run(base_url: str = DEFAULT_BASE_URL) -> None:
    run_id = _run_id()
    print(f"\n########## SUITE: register  (base={base_url}, run_id={run_id}) ##########")
    ctx = Ctx(base_url=base_url, password=PASSWORD, run_id=run_id)

    for level in REGISTER_LEVELS:
        ctx.level = level
        await run_group("register", level, REGISTER_THRESHOLD, register_task, ctx,
                        metric_label="端到端时延")


def main() -> None:
    parser = argparse.ArgumentParser(description="Register concurrency load test")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    args = parser.parse_args()
    try:
        asyncio.run(run(args.base_url))
        print("\n[register] ALL GROUPS PASSED")
    except LoadTestFailed as e:
        print(f"\n[register] FAILED: {e}")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
