Below is a complete Python REST API using **FastAPI** and **httpx**. It forwards common HTTP requests through a randomly chosen healthy proxy, with:

- Random proxy selection from a configured pool
- Passive proxy health tracking with a circuit breaker
- Retry with backoff for transient errors:
  - Timeouts
  - Connection/transport errors
  - `500`, `502`, `503`, `504`, `408`
- Special handling for `429`:
  - Blacklists the proxy for the target domain for a TTL
  - Respects `Retry-After` when present
- Persistent/non-retryable errors:
  - Most `4xx` responses are returned immediately without retry
- Streaming responses
- `/health` and `/proxies` inspection endpoints

---

## 1. Requirements

```text
fastapi
uvicorn[standard]
httpx
```

Install:

```bash
pip install fastapi 'uvicorn[standard]' httpx
```

---

## 2. `app.py`

```python
from __future__ import annotations

import asyncio
import logging
import os
import random
import time
from contextlib import asynccontextmanager
from dataclasses import dataclass
from datetime import timezone
from email.utils import parsedate_to_datetime
from typing import AsyncIterator, Dict, List, Optional, Set, Tuple
from urllib.parse import urlparse

import httpx
from fastapi import FastAPI, Query, Request, Response
from fastapi.responses import JSONResponse, StreamingResponse


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("proxy-gateway")


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------


def _env_float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, default))
    except (TypeError, ValueError):
        return default


def _env_int(name: str, default: int) -> int:
    try:
        return int(float(os.getenv(name, default)))
    except (TypeError, ValueError):
        return default


def _normalize_proxy_url(proxy: str) -> str:
    proxy = proxy.strip()
    if not proxy:
        return ""
    if "://" not in proxy:
        proxy = f"http://{proxy}"
    return proxy


_RAW_PROXY_URLS = [
    p.strip()
    for p in os.getenv(
        "PROXY_URLS",
        "http://127.0.0.1:8080,http://127.0.0.1:8081",
    ).split(",")
    if p.strip()
]

PROXY_URLS: List[str] = [_normalize_proxy_url(p) for p in _RAW_PROXY_URLS]

MAX_ATTEMPTS = _env_int("MAX_ATTEMPTS", 3)

TIMEOUT_CONNECT = _env_float("TIMEOUT_CONNECT", 5.0)
TIMEOUT_READ = _env_float("TIMEOUT_READ", 30.0)
TIMEOUT_WRITE = _env_float("TIMEOUT_WRITE", 30.0)
TIMEOUT_POOL = _env_float("TIMEOUT_POOL", 10.0)

PROXY_FAILURE_THRESHOLD = _env_int("PROXY_FAILURE_THRESHOLD", 3)
PROXY_COOLDOWN_SECONDS = _env_float("PROXY_COOLDOWN_SECONDS", 30.0)
DOMAIN_BLACKLIST_TTL = _env_float("DOMAIN_BLACKLIST_TTL", 60.0)

RETRY_BACKOFF_BASE = _env_float("RETRY_BACKOFF_BASE", 0.5)
RETRY_BACKOFF_MAX = _env_float("RETRY_BACKOFF_MAX", 8.0)

VERIFY_TLS = os.getenv("VERIFY_TLS", "true").strip().lower() not in {
    "0",
    "false",
    "no",
    "off",
}


# ---------------------------------------------------------------------------
# HTTP status policy
# ---------------------------------------------------------------------------

# These are considered transient/retryable.
TRANSIENT_HTTP_STATUS = {
    408,  # Request Timeout
    500,  # Internal Server Error
    502,  # Bad Gateway
    503,  # Service Unavailable
    504,  # Gateway Timeout
}

# These are considered persistent/non-retryable.
# For 4xx, the gateway returns the upstream response immediately.
# 501 is treated as non-retryable here, but you may adjust this.
PERSISTENT_HTTP_STATUS = {
    400,
    401,
    402,
    403,
    404,
    405,
    406,
    409,
    410,
    411,
    413,
    414,
    415,
    418,
    422,
    425,
    426,
    428,
    431,
    451,
    501,
}


# Hop-by-hop and other headers that should not be forwarded blindly.
HOP_BY_HOP = {
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "proxy-connection",
    "te",
    "trailer",
    "transfer-encoding",
    "upgrade",
    "host",
}

REQUEST_DROP = HOP_BY_HOP | {"content-length", "expect"}
RESPONSE_DROP = HOP_BY_HOP | {"content-length", "content-encoding"}


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _format_host(hostname: Optional[str], port: Optional[int]) -> str:
    if not hostname:
        return ""

    if ":" in hostname:
        hostname = f"[{hostname}]"

    if port:
        return f"{hostname}:{port}"

    return hostname


def _redact_proxy(proxy: str) -> str:
    """
    Redact credentials from proxy URLs for logging and inspection endpoints.
    """
    p = urlparse(proxy)
    if not p.netloc:
        return proxy

    host = _format_host(p.hostname, p.port)
    scheme = p.scheme or "http"
    return f"{scheme}://{host}"


def parse_retry_after(value: Optional[str]) -> Optional[float]:
    """
    Parse Retry-After header as seconds or HTTP date.
    """
    if not value:
        return None

    value = value.strip()

    try:
        return max(0.0, float(value))
    except ValueError:
        pass

    try:
        dt = parsedate_to_datetime(value)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return max(0.0, dt.timestamp() - time.time())
    except Exception:
        return None


def backoff_delay(attempt: int) -> float:
    delay = RETRY_BACKOFF_BASE * (2 ** attempt)
    delay = min(delay, RETRY_BACKOFF_MAX)
    return delay + random.uniform(0, 0.1)


def filter_request_headers(headers) -> Dict[str, str]:
    out: Dict[str, str] = {}
    for k, v in headers.items():
        if k.lower() in REQUEST_DROP:
            continue
        out[k] = v
    return out


def filter_response_headers(headers, response: httpx.Response) -> Dict[str, str]:
    # Keep Content-Length only for responses that must not have a body.
    no_body = response.status_code in {204, 304}

    out: Dict[str, str] = {}
    for k, v in headers.items():
        lk = k.lower()
        if lk in RESPONSE_DROP:
            if lk == "content-length" and no_body:
                out[k] = v
            continue
        out[k] = v
    return out


def _request_content(method: str, body: bytes) -> Optional[bytes]:
    """
    If the client sent no body, send an explicit empty body for methods that
    commonly expect one. This preserves Content-Length: 0 for empty POST/PUT/etc.
    """
    if body:
        return body

    if method.upper() in {"POST", "PUT", "PATCH", "DELETE"}:
        return b""

    return None


# ---------------------------------------------------------------------------
# Proxy state
# ---------------------------------------------------------------------------


@dataclass
class ProxyState:
    proxy: str
    consecutive_failures: int = 0
    total_failures: int = 0
    total_successes: int = 0
    last_success: Optional[float] = None
    last_failure: Optional[float] = None
    last_error: Optional[str] = None
    circuit_open_until: float = 0.0


class ProxyPool:
    def __init__(
        self,
        proxies: List[str],
        failure_threshold: int,
        cooldown: float,
        domain_blacklist_ttl: float,
    ) -> None:
        self.proxies = proxies
        self.failure_threshold = failure_threshold
        self.cooldown = cooldown
        self.domain_blacklist_ttl = domain_blacklist_ttl

        self.states: Dict[str, ProxyState] = {
            p: ProxyState(proxy=p) for p in proxies
        }

        self.clients: Dict[str, httpx.AsyncClient] = {}

        # domain -> proxy -> expiry_timestamp
        self.domain_blacklist: Dict[str, Dict[str, float]] = {}

        self._lock = asyncio.Lock()
        self._rng = random.SystemRandom()

    async def get_client(self, proxy: str) -> httpx.AsyncClient:
        async with self._lock:
            client = self.clients.get(proxy)
            if client is None or client.is_closed:
                client = httpx.AsyncClient(
                    proxy=proxy,
                    verify=VERIFY_TLS,
                    timeout=httpx.Timeout(
                        TIMEOUT_READ,
                        connect=TIMEOUT_CONNECT,
                        write=TIMEOUT_WRITE,
                        pool=TIMEOUT_POOL,
                    ),
                    follow_redirects=False,
                    headers={"User-Agent": "python-proxy-gateway/1.0"},
                )
                self.clients[proxy] = client
            return client

    def _cleanup_blacklist(self, now: float) -> None:
        for domain in list(self.domain_blacklist.keys()):
            expired = [
                p
                for p, exp in self.domain_blacklist[domain].items()
                if exp <= now
            ]
            for p in expired:
                del self.domain_blacklist[domain][p]

            if not self.domain_blacklist[domain]:
                del self.domain_blacklist[domain]

    async def choose_proxy(
        self,
        domain: str,
        exclude: Optional[Set[str]] = None,
    ) -> Tuple[Optional[str], float]:
        """
        Choose a random proxy that is:
        - not circuit-open
        - not blacklisted for the given domain

        Returns:
            (proxy_url, wait_seconds)

        If no proxy is available, proxy_url is None and wait_seconds is the
        approximate time until the earliest restriction expires.
        """
        now = time.time()
        async with self._lock:
            self._cleanup_blacklist(now)

            exclude = exclude or set()
            available: List[str] = []
            fallback: List[str] = []
            earliest = 0.0

            for p in self.proxies:
                state = self.states[p]

                if state.circuit_open_until > now:
                    earliest = max(earliest, state.circuit_open_until)
                    continue

                blacklisted_until = self.domain_blacklist.get(domain, {}).get(p, 0.0)
                if blacklisted_until > now:
                    earliest = max(earliest, blacklisted_until)
                    continue

                fallback.append(p)
                if p not in exclude:
                    available.append(p)

            if available:
                return self._rng.choice(available), 0.0

            # If excluding already-tried proxies leaves nothing, allow retrying
            # a previously tried proxy as a last resort.
            if fallback:
                return self._rng.choice(fallback), 0.0

            return None, max(0.0, earliest - now)

    async def record_success(self, proxy: str) -> None:
        now = time.time()
        async with self._lock:
            state = self.states.get(proxy)
            if state is None:
                return

            state.consecutive_failures = 0
            state.total_successes += 1
            state.last_success = now
            state.circuit_open_until = 0.0

    async def record_failure(self, proxy: str, error: str) -> None:
        now = time.time()
        async with self._lock:
            state = self.states.get(proxy)
            if state is None:
                return

            state.consecutive_failures += 1
            state.total_failures += 1
            state.last_failure = now
            state.last_error = error[:500]

            if state.consecutive_failures >= self.failure_threshold:
                state.circuit_open_until = now + self.cooldown
                logger.warning(
                    "Proxy %s circuit-open for %.1fs after %d consecutive failures: %s",
                    _redact_proxy(proxy),
                    self.cooldown,
                    state.consecutive_failures,
                    error,
                )

    async def blacklist_domain(
        self,
        proxy: str,
        domain: str,
        ttl: Optional[float] = None,
        retry_after: Optional[float] = None,
    ) -> None:
        now = time.time()
        ttl = self.domain_blacklist_ttl if ttl is None else ttl

        if retry_after is not None:
            ttl = min(ttl, retry_after)

        # Clamp to a sane range.
        ttl = max(1.0, min(ttl, 3600.0))

        async with self._lock:
            self.domain_blacklist.setdefault(domain, {})[proxy] = now + ttl
            logger.info(
                "Blacklisted proxy %s for domain %s for %.1fs",
                _redact_proxy(proxy),
                domain,
                ttl,
            )

    async def stats(self) -> Dict:
        now = time.time()
        async with self._lock:
            self._cleanup_blacklist(now)

            return {
                "proxies": [
                    {
                        "id": i,
                        "proxy": _redact_proxy(state.proxy),
                        "consecutive_failures": state.consecutive_failures,
                        "total_successes": state.total_successes,
                        "total_failures": state.total_failures,
                        "last_success": state.last_success,
                        "last_failure": state.last_failure,
                        "last_error": state.last_error,
                        "circuit_open": state.circuit_open_until > now,
                        "circuit_open_until": state.circuit_open_until,
                    }
                    for i, state in enumerate(self.states.values())
                ],
                "domain_blacklists": {
                    domain: {
                        _redact_proxy(proxy): max(0.0, expiry - now)
                        for proxy, expiry in items.items()
                    }
                    for domain, items in self.domain_blacklist.items()
                },
            }

    async def close(self) -> None:
        async with self._lock:
            for client in self.clients.values():
                await client.aclose()
            self.clients.clear()


# ---------------------------------------------------------------------------
# Forwarding
# ---------------------------------------------------------------------------


async def stream_response(response: httpx.Response) -> StreamingResponse:
    async def iterator() -> AsyncIterator[bytes]:
        try:
            async for chunk in response.aiter_bytes():
                yield chunk
        finally:
            await response.aclose()

    return StreamingResponse(
        iterator(),
        status_code=response.status_code,
        headers=filter_response_headers(response.headers, response),
        media_type=None,
    )


async def forward_request(
    pool: ProxyPool,
    target: str,
    method: str,
    headers: Dict[str, str],
    body: Optional[bytes],
) -> Response:
    parsed = urlparse(target)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        return JSONResponse(
            status_code=400,
            content={"detail": "url must be an absolute http(s) URL"},
        )

    domain = _format_host(parsed.hostname, parsed.port).lower()
    if not domain:
        return JSONResponse(
            status_code=400,
            content={"detail": "Could not determine target host"},
        )

    if not pool.proxies:
        return JSONResponse(
            status_code=503,
            content={"detail": "No proxies configured"},
        )

    last_status: Optional[int] = None
    last_detail: Optional[str] = None
    last_wait: float = 0.0
    tried: Set[str] = set()

    content = _request_content(method, body or b"")

    for attempt in range(MAX_ATTEMPTS):
        proxy, wait = await pool.choose_proxy(domain, tried)

        if proxy is None:
            last_detail = "No proxy available for this domain"
            last_wait = wait
            if wait > 0:
                await asyncio.sleep(min(wait, RETRY_BACKOFF_MAX))
            continue

        tried.add(proxy)

        try:
            client = await pool.get_client(proxy)
            response = await client.request(
                method=method,
                url=target,
                headers=headers,
                content=content,
            )
        except httpx.TimeoutException as exc:
            last_status = 504
            last_detail = (
                f"Timeout via proxy {_redact_proxy(proxy)}: {exc.__class__.__name__}"
            )
            await pool.record_failure(proxy, last_detail)
            await asyncio.sleep(backoff_delay(attempt))
            continue

        except httpx.TransportError as exc:
            last_status = 502
            last_detail = (
                f"Transport error via proxy {_redact_proxy(proxy)}: "
                f"{exc.__class__.__name__}"
            )
            await pool.record_failure(proxy, last_detail)
            await asyncio.sleep(backoff_delay(attempt))
            continue

        except Exception as exc:
            last_status = 500
            last_detail = (
                f"Unexpected error via proxy {_redact_proxy(proxy)}: "
                f"{exc.__class__.__name__}"
            )
            await pool.record_failure(proxy, last_detail)
            await asyncio.sleep(backoff_delay(attempt))
            continue

        status = response.status_code

        # -------------------------------------------------------------------
        # 429: rate limited. Blacklist this proxy for this target domain.
        # -------------------------------------------------------------------
        if status == 429:
            retry_after = parse_retry_after(response.headers.get("retry-after"))
            await pool.blacklist_domain(proxy, domain, retry_after=retry_after)

            last_status = 429
            last_detail = (
                f"429 via proxy {_redact_proxy(proxy)}; "
                f"blacklisted for domain {domain}"
            )

            # Release the connection before trying another proxy.
            await response.aclose()
            continue

        # -------------------------------------------------------------------
        # 407: proxy authentication required. Try another proxy.
        # -------------------------------------------------------------------
        if status == 407:
            last_status = 407
            last_detail = f"407 via proxy {_redact_proxy(proxy)}"
            await pool.record_failure(proxy, last_detail)
            await response.aclose()
            await asyncio.sleep(backoff_delay(attempt))
            continue

        # -------------------------------------------------------------------
        # Transient HTTP statuses: retry through another proxy.
        # -------------------------------------------------------------------
        if status in TRANSIENT_HTTP_STATUS:
            last_status = status
            last_detail = f"Transient HTTP {status} via proxy {_redact_proxy(proxy)}"
            await pool.record_failure(proxy, last_detail)
            await response.aclose()
            await asyncio.sleep(backoff_delay(attempt))
            continue

        # -------------------------------------------------------------------
        # Persistent/non-retryable statuses: return upstream response.
        # -------------------------------------------------------------------
        if status in PERSISTENT_HTTP_STATUS:
            if status < 500:
                # The proxy delivered an upstream 4xx response.
                await pool.record_success(proxy)
            else:
                # Non-retryable 5xx, e.g. 501.
                await pool.record_failure(
                    proxy,
                    f"Persistent/non-retryable HTTP {status} via proxy "
                    f"{_redact_proxy(proxy)}",
                )

            return await stream_response(response)

        # -------------------------------------------------------------------
        # Other 5xx statuses: treat as transient/proxy problem.
        # -------------------------------------------------------------------
        if 500 <= status < 600:
            last_status = status
            last_detail = f"HTTP {status} via proxy {_redact_proxy(proxy)}"
            await pool.record_failure(proxy, last_detail)
            await response.aclose()
            await asyncio.sleep(backoff_delay(attempt))
            continue

        # -------------------------------------------------------------------
        # Success or redirect: return upstream response.
        # -------------------------------------------------------------------
        await pool.record_success(proxy)
        return await stream_response(response)

    # -----------------------------------------------------------------------
    # All attempts exhausted.
    # -----------------------------------------------------------------------
    if last_status is None:
        return JSONResponse(
            status_code=503,
            content={"detail": "No proxy available"},
        )

    if last_status == 429:
        retry_after = int(
            min(
                3600.0,
                max(1.0, last_wait if last_wait > 0 else DOMAIN_BLACKLIST_TTL),
            )
        )
        return JSONResponse(
            status_code=429,
            content={
                "detail": last_detail
                or "All proxies are rate-limited for this domain"
            },
            headers={"Retry-After": str(retry_after)},
        )

    if last_status in {407, 502, 504}:
        return JSONResponse(
            status_code=last_status,
            content={"detail": last_detail or "Request failed"},
        )

    return JSONResponse(
        status_code=502,
        content={"detail": last_detail or "Request failed"},
    )


# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------


@asynccontextmanager
async def lifespan(app: FastAPI):
    pool = ProxyPool(
        proxies=PROXY_URLS,
        failure_threshold=PROXY_FAILURE_THRESHOLD,
        cooldown=PROXY_COOLDOWN_SECONDS,
        domain_blacklist_ttl=DOMAIN_BLACKLIST_TTL,
    )
    app.state.pool = pool
    logger.info("Starting proxy gateway with %d proxies", len(PROXY_URLS))
    yield
    await pool.close()


app = FastAPI(title="Proxy Gateway", lifespan=lifespan)


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.get("/proxies")
async def proxies(request: Request):
    return await request.app.state.pool.stats()


# ---------------------------------------------------------------------------
# Endpoint style 1:
#   /proxy?url=https://example.com/path
#
# Use this when you want exact control over the target URL.
# ---------------------------------------------------------------------------
@app.api_route(
    "/proxy",
    methods=["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"],
)
async def proxy_by_url(
    request: Request,
    target: str = Query(..., alias="url"),
):
    body = await request.body()
    headers = filter_request_headers(request.headers)
    return await forward_request(
        request.app.state.pool,
        target,
        request.method,
        headers,
        body,
    )


# ---------------------------------------------------------------------------
# Endpoint style 2:
#   /proxy/https://example.com/path
#   /proxy/example.com/path
#
# The query string of the gateway request is appended to the target URL.
# ---------------------------------------------------------------------------
@app.api_route(
    "/proxy/{target:path}",
    methods=["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"],
)
async def proxy_by_path(request: Request, target: str):
    target = target.lstrip("/")

    if not target.startswith(("http://", "https://")):
        target = f"https://{target}"

    if request.url.query:
        sep = "&" if "?" in target else "?"
        target = f"{target}{sep}{request.url.query}"

    body = await request.body()
    headers = filter_request_headers(request.headers)
    return await forward_request(
        request.app.state.pool,
        target,
        request.method,
        headers,
        body,
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="0.0.0.0", port=8000)
```

---

## 3. Run

Set your proxy URLs:

```bash
export PROXY_URLS="http://user1:pass1@proxy1.example.com:3128,http://proxy2.example.com:3128"
```

Optional tuning:

```bash
export MAX_ATTEMPTS=3
export PROXY_FAILURE_THRESHOLD=3
export PROXY_COOLDOWN_SECONDS=30
export DOMAIN_BLACKLIST_TTL=60
export RETRY_BACKOFF_BASE=0.5
export RETRY_BACKOFF_MAX=8
export TIMEOUT_CONNECT=5
export TIMEOUT_READ=30
export TIMEOUT_WRITE=30
export TIMEOUT_POOL=10
export VERIFY_TLS=true
```

Start:

```bash
uvicorn app:app --host 0.0.0.0 --port 8000
```

---

## 4. Example requests

### GET

```bash
curl -v 'http://localhost:8000/proxy?url=https://httpbin.org/get'
```

Or:

```bash
curl -v 'http://localhost:8000/proxy/https://httpbin.org/get'
```

### GET with query string

Using the path style:

```bash
curl -v 'http://localhost:8000/proxy/https://httpbin.org/get?foo=bar&baz=qux'
```

Using the query-param style, be careful with `&`. The target URL should be properly encoded if it contains query parameters:

```bash
curl -v 'http://localhost:8000/proxy?url=https://httpbin.org/get%3Ffoo%3Dbar%26baz%3Dqux'
```

### POST with JSON body

```bash
curl -X POST \
  'http://localhost:8000/proxy?url=https://httpbin.org/post' \
  -H 'Content-Type: application/json' \
  -d '{"hello":"world"}'
```

Or path style:

```bash
curl -X POST \
  'http://localhost:8000/proxy/https://httpbin.org/post' \
  -H 'Content-Type: application/json' \
  -d '{"hello":"world"}'
```

### PUT

```bash
curl -X PUT \
  'http://localhost:8000/proxy/https://httpbin.org/put' \
  -H 'Content-Type: application/json' \
  -d '{"id":123}'
```

### DELETE

```bash
curl -X DELETE 'http://localhost:8000/proxy/https://httpbin.org/delete'
```

### Inspect proxy health and blacklists

```bash
curl http://localhost:8000/proxies
```

Example response shape:

```json
{
  "proxies": [
    {
      "id": 0,
      "proxy": "http://proxy1.example.com:3128",
      "consecutive_failures": 0,
      "total_successes": 10,
      "total_failures": 1,
      "last_success": 1712345678.123,
      "last_failure": 1712345600.001,
      "last_error": "Transient HTTP 503 via proxy http://proxy1.example.com:3128",
      "circuit_open": false,
      "circuit_open_until": 0.0
    }
  ],
  "domain_blacklists": {
    "example.com:443": {
      "http://proxy1.example.com:3128": 42.5
    }
  }
}
```

---

## 5. Behavior summary

### Proxy selection

For each request:

1. Extract the target domain, including port if present.
2. Choose a random proxy that is:
   - not circuit-open
   - not blacklisted for that target domain
3. Avoid previously tried proxies in the same request when possible.
4. If all healthy proxies were already tried, fall back to retrying one of them.

---

### Health tracking

Each proxy has:

- `consecutive_failures`
- `total_failures`
- `total_successes`
- `last_success`
- `last_failure`
- `last_error`
- `circuit_open_until`

When a proxy reaches `PROXY_FAILURE_THRESHOLD` consecutive failures, it is circuit-opened for `PROXY_COOLDOWN_SECONDS`.

A success resets the consecutive failure count and closes the circuit.

---

### Transient errors

Retried with exponential backoff:

- `httpx.TimeoutException`
- `httpx.TransportError`
- Unexpected exceptions
- HTTP statuses:
  - `408`
  - `500`
  - `502`
  - `503`
  - `504`

Other `5xx` statuses are also retried by default.

---

### `429` handling

When a proxy returns `429`:

1. The proxy is blacklisted for the target domain.
2. The blacklist TTL is:
   - `Retry-After` if present and smaller than the configured TTL
   - otherwise `DOMAIN_BLACKLIST_TTL`
3. The request is retried through another proxy if available.
4. If all proxies are blacklisted for that domain, the API waits for the earliest expiry and eventually returns `429` with a `Retry-After` header.

This gives you the requested behavior:

> maintaining a blacklist of servers for given domain requests

---

### Persistent errors

Most `4xx` responses are treated as persistent/non-retryable and are returned directly to the client:

- `400`
- `401`
- `402`
- `403`
- `404`
- `405`
- `406`
- `409`
- `410`
- `411`
- `413`
- `414`
- `415`
- `418`
- `422`
- `425`
- `426`
- `428`
- `431`
- `451`

`407` is treated as proxy-specific and is retried through other proxies.

`501` is treated as non-retryable in this implementation, but you can adjust that if your environment behaves differently.

---

## 6. Important production notes

This implementation is intentionally self-contained and uses in-memory state. For production, consider adding:

### Multi-worker state sharing

If you run multiple Uvicorn workers or multiple instances, the in-memory proxy health and blacklists will not be shared.

Use Redis, Memcached, or another shared store for:

- proxy failure counters
- circuit breaker state
- per-domain blacklists

---

### Active health checks

This implementation uses passive health tracking only. You can add a background task that periodically probes each proxy against a known endpoint.

---

### Header sanitization

The current implementation forwards most client headers except hop-by-hop and a few unsafe ones.

Depending on your threat model, you may also want to strip or normalize:

- `X-Forwarded-For`
- `X-Real-IP`
- `Forwarded`
- `Origin`
- `Referer`
- custom internal headers

---

### Large request bodies

The current implementation reads the request body into memory.

For large uploads/downloads, consider:

- Streaming request bodies into `httpx`
- Limiting request size
- Using spooling/temp files
- Adding timeout and size limits

---

### Raw response bytes

The current implementation uses `response.aiter_bytes()`, which decodes content encoding. It then removes:

- `Content-Encoding`
- `Content-Length`

If you need byte-for-byte passthrough, use `response.aiter_raw()` and preserve `Content-Encoding` carefully.

---

### Protect `/proxies`

The `/proxies` endpoint exposes operational metadata. In production, protect it with authentication or restrict it to internal networks.

---

### Gateway authentication

Add authentication to the gateway itself if clients are untrusted.

---

### Rate limiting

Add gateway-level rate limiting to prevent abuse.

---

### Metrics

Add Prometheus metrics for:

- requests per proxy
- errors per proxy
- retries
- 429 blacklists
- circuit breaker state
- latency

---

This should give you a solid starting point for a proxy-forwarding REST API with health awareness, retry behavior, domain-scoped `429` blacklisting, and persistent-error passthrough.
