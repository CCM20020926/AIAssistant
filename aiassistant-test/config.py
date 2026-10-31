"""Central configuration for the AI Assistant load tests.

All endpoints are reached through Nginx (default http://127.0.0.1:80), which
reverse-proxies to the gateway / MinerU exactly as in production.
"""
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Optional
import dotenv

dotenv.load_dotenv()

# Nginx entry point. Override with --base-url or the AIASSISTANT_BASE_URL env var.
DEFAULT_BASE_URL = os.getenv("AIASSISTANT_BASE_URL", "http://127.0.0.1:80")

# ---- Per-suite concurrency levels and latency thresholds (seconds) ----
LOGIN_LEVELS: List[int] = [10, 100, 500, 1000]
LOGIN_THRESHOLD: float = 5.0

REGISTER_LEVELS: List[int] = [10, 100, 500]
REGISTER_THRESHOLD: float = 10.0

CHAT_LEVELS: List[int] = [10, 100]
CHAT_THRESHOLD: float = 10.0
CHAT_INPUT: str = "Hello!"

PARSE_LEVELS: List[int] = [1, 10]
PARSE_THRESHOLD: float = 60.0
PARSE_FILE: Path = Path(__file__).resolve().parent / "example.pdf"

# Shared credential for auto-provisioned virtual users.
PASSWORD: str = "Test@12345"

# ---- Over-limit retry mechanism ----
# A request rejected with this HTTP status is treated as "over the concurrency
# limit", recorded, and re-run after RETRY_BACKOFF seconds, up to MAX_RETRY_ROUNDS.
OVER_LIMIT_STATUS: int = 429
RETRY_BACKOFF: float = 2.0
MAX_RETRY_ROUNDS: int = 3

# Endpoints (paths are appended to the base URL).
REGISTER_PATH = "/api/auth/register"
LOGIN_PATH = "/api/auth/login"
CHAT_PATH = "/api/chat/stream"
DOC_PARSE_PATH = "/api/document/parse"


@dataclass
class Ctx:
    """Per-suite runtime context handed to every request task."""
    base_url: str
    password: str = PASSWORD
    run_id: str = ""

    # current concurrency level (used to keep generated usernames unique per group)
    level: int = 0

    # login suite
    username: Optional[str] = None

    # chat suite
    chat_input: str = CHAT_INPUT
    tokens: List[str] = field(default_factory=list)

    # document-parse suite
    file_bytes: Optional[bytes] = None
    file_name: str = "example.pdf"
