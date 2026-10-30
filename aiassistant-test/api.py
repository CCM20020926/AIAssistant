"""Endpoint callers. Each *_task coroutine matches the runner Task signature and
returns a Sample whose latency is measured from the common batch start t0.
"""
import asyncio
import json
import time
from typing import List, Optional

import aiohttp

from config import (
    Ctx, CHAT_PATH, DOC_PARSE_PATH, LOGIN_PATH, OVER_LIMIT_STATUS, REGISTER_PATH,
)
from runner import Sample


def _unique_username(run_id: str, level: int, index: int) -> str:
    # 3..32 chars, unique across levels and indices within a run.
    return f"lt{run_id}{level:04d}{index:05d}"


async def _register(session: aiohttp.ClientSession, base_url: str,
                    username: str, password: str) -> None:
    payload = {"username": username, "password": password, "nickname": username}
    async with session.post(f"{base_url}{REGISTER_PATH}", json=payload) as resp:
        text = await resp.text()
        if resp.status != 200:
            raise RuntimeError(f"register HTTP {resp.status}: {text[:200]}")
        try:
            body = json.loads(text)
        except json.JSONDecodeError:
            raise RuntimeError(f"register bad JSON: {text[:200]}")
        if body.get("code") != 200:
            raise RuntimeError(f"register code={body.get('code')} msg={body.get('message')}")


async def _login_token(session: aiohttp.ClientSession, base_url: str,
                       username: str, password: str) -> str:
    payload = {"username": username, "password": password}
    async with session.post(f"{base_url}{LOGIN_PATH}", json=payload) as resp:
        text = await resp.text()
        if resp.status != 200:
            raise RuntimeError(f"login HTTP {resp.status}: {text[:200]}")
        body = json.loads(text)
        if body.get("code") != 200:
            raise RuntimeError(f"login code={body.get('code')} msg={body.get('message')}")
        token = (body.get("data") or {}).get("token")
        if not token:
            raise RuntimeError("login response has no data.token")
        return token


async def provision_user(base_url: str, run_id: str) -> str:
    """Setup helper: register one user and return its username (for the login suite)."""
    connector = aiohttp.TCPConnector(limit=0)
    timeout = aiohttp.ClientTimeout(total=60)
    async with aiohttp.ClientSession(connector=connector, timeout=timeout, trust_env=False) as session:
        username = f"lt{run_id}login"
        try:
            await _register(session, base_url, username, "Test@12345")
        except RuntimeError as e:
            # Already exists from a previous run is fine; login will still work.
            print(f"  [setup] register note: {e}")
        return username


async def provision_tokens(base_url: str, run_id: str, count: int,
                           password: str = "Test@12345") -> List[str]:
    """Setup helper: register+login `count` distinct users, return their tokens."""
    connector = aiohttp.TCPConnector(limit=0)
    timeout = aiohttp.ClientTimeout(total=120)
    sem = asyncio.Semaphore(10)
    tokens: List[Optional[str]] = [None] * count

    async with aiohttp.ClientSession(connector=connector, timeout=timeout, trust_env=False) as session:
        async def one(i: int) -> None:
            async with sem:
                username = _unique_username(run_id, 0, i)
                try:
                    await _register(session, base_url, username, password)
                except RuntimeError:
                    pass  # tolerate "already exists"
                tokens[i] = await _login_token(session, base_url, username, password)

        await asyncio.gather(*(one(i) for i in range(count)))

    result = [t for t in tokens if t]
    if len(result) < count:
        raise RuntimeError(f"only provisioned {len(result)}/{count} tokens")
    return result


# ---------------------------------------------------------------------------
# Timed request tasks
# ---------------------------------------------------------------------------

async def login_task(session: aiohttp.ClientSession, ctx: Ctx, index: int, t0: float) -> Sample:
    payload = {"username": ctx.username, "password": ctx.password}
    url = f"{ctx.base_url}{LOGIN_PATH}"
    try:
        async with session.post(url, json=payload) as resp:
            text = await resp.text()
            finish = time.perf_counter()
            if resp.status != 200:
                return Sample(False, finish - t0, error=f"HTTP {resp.status}: {text[:160]}",
                                status=resp.status, over_limit=(resp.status == OVER_LIMIT_STATUS))
            body = json.loads(text)
            if body.get("code") != 200 or not (body.get("data") or {}).get("token"):
                return Sample(False, finish - t0, error=f"bad body: {text[:160]}", status=resp.status)
            return Sample(True, finish - t0, status=resp.status)
    except Exception as e:  # noqa: BLE001
        return Sample(False, time.perf_counter() - t0, f"{type(e).__name__}: {e}")


async def register_task(session: aiohttp.ClientSession, ctx: Ctx, index: int, t0: float) -> Sample:
    # index is unique per request; embed the level via ctx to avoid cross-group clashes.
    username = _unique_username(ctx.run_id, ctx.level, index)
    payload = {"username": username, "password": ctx.password, "nickname": username}
    url = f"{ctx.base_url}{REGISTER_PATH}"
    try:
        async with session.post(url, json=payload) as resp:
            text = await resp.text()
            finish = time.perf_counter()
            if resp.status != 200:
                return Sample(False, finish - t0, error=f"HTTP {resp.status}: {text[:160]}",
                                status=resp.status, over_limit=(resp.status == OVER_LIMIT_STATUS))
            body = json.loads(text)
            if body.get("code") != 200:
                return Sample(False, finish - t0,
                                error=f"code={body.get('code')} msg={body.get('message')}",
                                status=resp.status)
            return Sample(True, finish - t0, status=resp.status)
    except Exception as e:  # noqa: BLE001
        return Sample(False, time.perf_counter() - t0, f"{type(e).__name__}: {e}")


async def chat_first_token_task(session: aiohttp.ClientSession, ctx: Ctx, index: int, t0: float) -> Sample:
    token = ctx.tokens[index % len(ctx.tokens)]
    headers = {"Authorization": f"Bearer {token}", "Accept": "text/event-stream"}
    payload = {
        "content": ctx.chat_input,
        "conversationId": None,
        "deepThink": False,
        "webSearch": False,
        "pptMode": False,
        "knowledgeMode": False,
        "knowledgeBaseId": None,
    }
    url = f"{ctx.base_url}{CHAT_PATH}"
    try:
        async with session.post(url, json=payload, headers=headers) as resp:
            if resp.status != 200:
                text = await resp.text()
                return Sample(False, time.perf_counter() - t0,
                                error=f"HTTP {resp.status}: {text[:160]}",
                                status=resp.status, over_limit=(resp.status == OVER_LIMIT_STATUS))
            event: Optional[str] = None
            async for raw in resp.content:
                line = raw.decode("utf-8", "ignore").rstrip("\r\n")
                if line.startswith("event:"):
                    event = line[len("event:"):].strip()
                elif line.startswith("data:"):
                    data = line[len("data:"):].strip()
                    if not data:
                        continue
                    try:
                        obj = json.loads(data)
                    except json.JSONDecodeError:
                        obj = {}
                    etype = event or obj.get("type")
                    content = obj.get("content")
                    if etype in ("thinking", "answer") and content:
                        return Sample(True, time.perf_counter() - t0)  # first token
                    if etype == "error":
                        return Sample(False, time.perf_counter() - t0, f"error event: {content}")
                    if etype == "done":
                        return Sample(False, time.perf_counter() - t0, "done before first token")
            return Sample(False, time.perf_counter() - t0, "stream closed before first token")
    except Exception as e:  # noqa: BLE001
        return Sample(False, time.perf_counter() - t0, f"{type(e).__name__}: {e}")


async def doc_parse_task(session: aiohttp.ClientSession, ctx: Ctx, index: int, t0: float) -> Sample:
    url = f"{ctx.base_url}{DOC_PARSE_PATH}"
    try:
        form = aiohttp.FormData()
        form.add_field("files", ctx.file_bytes, filename=ctx.file_name,
                       content_type="application/pdf")
        async with session.post(url, data=form) as resp:
            body = await resp.read()
            finish = time.perf_counter()
            if resp.status != 200:
                return Sample(False, finish - t0, error=f"HTTP {resp.status}: {body[:160]!r}",
                                status=resp.status, over_limit=(resp.status == OVER_LIMIT_STATUS))
            return Sample(True, finish - t0, status=resp.status)
    except Exception as e:  # noqa: BLE001
        return Sample(False, time.perf_counter() - t0, f"{type(e).__name__}: {e}")
