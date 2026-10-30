"""Login endpoint load test: 10 / 100 / 1000 concurrent, latency < 5s to pass."""
import argparse
import asyncio
import time

from api import login_task, provision_user
from config import (
    Ctx, DEFAULT_BASE_URL, LOGIN_LEVELS, LOGIN_THRESHOLD, PASSWORD,
)
from runner import LoadTestFailed, run_group


def _run_id() -> str:
    return str(int(time.time()))[-6:]


async def run(base_url: str = DEFAULT_BASE_URL) -> None:
    run_id = _run_id()
    print(f"\n########## SUITE: login  (base={base_url}, run_id={run_id}) ##########")
    username = await provision_user(base_url, run_id)
    ctx = Ctx(base_url=base_url, password=PASSWORD, run_id=run_id, username=username)

    for level in LOGIN_LEVELS:
        ctx.level = level
        await run_group("login", level, LOGIN_THRESHOLD, login_task, ctx,
                        metric_label="端到端时延")


def main() -> None:
    parser = argparse.ArgumentParser(description="Login concurrency load test")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    args = parser.parse_args()
    try:
        asyncio.run(run(args.base_url))
        print("\n[login] ALL GROUPS PASSED")
    except LoadTestFailed as e:
        print(f"\n[login] FAILED: {e}")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
