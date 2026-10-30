"""Run every load-test suite in order. Stops at the first failing group and
exits non-zero. Suites: login -> register -> chat -> document-parse.

Usage:
    python run_all.py [--base-url http://127.0.0.1:80] [--only login,chat]
"""
import argparse
import asyncio
import sys

import test_chat
import test_document_parse
import test_login
import test_register
from config import DEFAULT_BASE_URL
from runner import LoadTestFailed

SUITES = {
    "login": test_login.run,
    "register": test_register.run,
    "chat": test_chat.run,
    "document-parse": test_document_parse.run,
}


async def run_all(base_url: str, only) -> int:
    names = only if only else list(SUITES.keys())
    for name in names:
        if name not in SUITES:
            print(f"unknown suite: {name}")
            return 2
        try:
            await SUITES[name](base_url)
            print(f"\n[{name}] ALL GROUPS PASSED")
        except LoadTestFailed as e:
            print(f"\n[{name}] FAILED: {e}")
            print("\n================ OVERALL: FAILED ================")
            return 1
    print("\n================ OVERALL: PASSED ================")
    return 0


def main() -> None:
    parser = argparse.ArgumentParser(description="AI Assistant concurrency load tests")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL,
                        help="Nginx entry point (default: %(default)s)")
    parser.add_argument("--only", default="",
                        help="comma-separated subset of: login,register,chat,document-parse")
    args = parser.parse_args()

    only = [s.strip() for s in args.only.split(",") if s.strip()]
    code = asyncio.run(run_all(args.base_url, only))
    sys.exit(code)


if __name__ == "__main__":
    main()
