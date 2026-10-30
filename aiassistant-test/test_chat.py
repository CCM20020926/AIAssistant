"""Chat endpoint load test: 10 / 100 concurrent, time-to-first-token < 10s.

Input is fixed to "Hello!". Each concurrent request uses its own provisioned
token so the gateway per-user chat limiter is not tripped. First-token latency
is measured from a common batch start; the stream is closed right after the
first token arrives (this triggers a server-side cancel, which is expected).
"""
import argparse
import asyncio
import time

from api import chat_first_token_task, provision_tokens
from config import (
    CHAT_INPUT, CHAT_LEVELS, CHAT_THRESHOLD, Ctx, DEFAULT_BASE_URL, PASSWORD,
)
from runner import LoadTestFailed, run_group


def _run_id() -> str:
    return str(int(time.time()))[-6:]


async def run(base_url: str = DEFAULT_BASE_URL) -> None:
    run_id = _run_id()
    print(f"\n########## SUITE: chat  (base={base_url}, run_id={run_id}) ##########")

    needed = max(CHAT_LEVELS)
    print(f"  [setup] provisioning {needed} virtual users (register+login)...")
    tokens = await provision_tokens(base_url, run_id, needed, PASSWORD)
    print(f"  [setup] got {len(tokens)} tokens")

    ctx = Ctx(base_url=base_url, password=PASSWORD, run_id=run_id,
              chat_input=CHAT_INPUT, tokens=tokens)

    for level in CHAT_LEVELS:
        ctx.level = level
        await run_group("chat", level, CHAT_THRESHOLD, chat_first_token_task, ctx,
                        metric_label="首字时延")


def main() -> None:
    parser = argparse.ArgumentParser(description="Chat first-token concurrency load test")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    args = parser.parse_args()
    try:
        asyncio.run(run(args.base_url))
        print("\n[chat] ALL GROUPS PASSED")
    except LoadTestFailed as e:
        print(f"\n[chat] FAILED: {e}")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
