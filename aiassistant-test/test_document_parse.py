"""Document-parse load test against the synchronous MinerU endpoint
POST /api/document/parse (Nginx -> MinerU /file_parse, multipart field "files").

Concurrency 1 / 10, latency < 40s to pass. Test document: example.pdf.
"""
import argparse
import asyncio

from api import doc_parse_task
from config import (
    Ctx, DEFAULT_BASE_URL, PARSE_FILE, PARSE_LEVELS, PARSE_THRESHOLD,
)
from runner import LoadTestFailed, run_group


async def run(base_url: str = DEFAULT_BASE_URL) -> None:
    print(f"\n########## SUITE: document-parse  (base={base_url}) ##########")

    if not PARSE_FILE.exists():
        raise LoadTestFailed(f"test document not found: {PARSE_FILE}")
    file_bytes = PARSE_FILE.read_bytes()
    print(f"  [setup] loaded {PARSE_FILE.name} ({len(file_bytes)} bytes)")

    ctx = Ctx(base_url=base_url, file_bytes=file_bytes, file_name=PARSE_FILE.name)

    for level in PARSE_LEVELS:
        ctx.level = level
        await run_group("document-parse", level, PARSE_THRESHOLD, doc_parse_task, ctx,
                        metric_label="端到端时延")


def main() -> None:
    parser = argparse.ArgumentParser(description="Document-parse concurrency load test")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    args = parser.parse_args()
    try:
        asyncio.run(run(args.base_url))
        print("\n[document-parse] ALL GROUPS PASSED")
    except LoadTestFailed as e:
        print(f"\n[document-parse] FAILED: {e}")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
