"""Inbound JWT verification, cursor signing and local rate limiting."""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import threading
import time
from collections import defaultdict, deque
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

import httpx
import jwt

from .errors import RuntimeFailure


@dataclass(frozen=True, slots=True)
class Principal:
    sub: str
    scopes: frozenset[str]

    def require(self, scope: str) -> None:
        if scope not in self.scopes:
            raise RuntimeFailure("FORBIDDEN", 403, "Forbidden")


class JwtVerifier:
    def __init__(
        self,
        issuer: str,
        audience: str,
        jwks_url: str,
        client: httpx.Client,
        now: Callable[[], float] = time.time,
    ) -> None:
        self.issuer = issuer
        self.audience = audience
        self.jwks_url = jwks_url
        self.client = client
        self.now = now
        self._keys: dict[str, Any] = {}
        self._loaded_at = 0.0

    def _load_keys(self, force: bool = False) -> None:
        if not force and self._keys and self.now() - self._loaded_at < 300:
            return
        try:
            response = self.client.get(self.jwks_url)
        except httpx.HTTPError as exc:
            raise RuntimeFailure("UNAUTHORIZED", 401, "Unauthorized") from exc
        if response.status_code != 200 or len(response.content) > 64 * 1024:
            raise RuntimeFailure("UNAUTHORIZED", 401, "Unauthorized")
        try:
            payload = response.json()
            keys = payload["keys"]
            loaded = {
                item["kid"]: jwt.PyJWK.from_dict(item).key
                for item in keys
                if isinstance(item, dict) and isinstance(item.get("kid"), str)
            }
        except (KeyError, TypeError, ValueError, jwt.PyJWTError) as exc:
            raise RuntimeFailure("UNAUTHORIZED", 401, "Unauthorized") from exc
        if not loaded:
            raise RuntimeFailure("UNAUTHORIZED", 401, "Unauthorized")
        self._keys = loaded
        self._loaded_at = self.now()

    def verify(self, token: str) -> Principal:
        try:
            header = jwt.get_unverified_header(token)
            if header.get("alg") != "RS256" or not isinstance(header.get("kid"), str):
                raise RuntimeFailure("UNAUTHORIZED", 401, "Unauthorized")
            kid = header["kid"]
            self._load_keys()
            key = self._keys.get(kid)
            if key is None:
                self._load_keys(force=True)
                key = self._keys.get(kid)
            if key is None:
                raise RuntimeFailure("UNAUTHORIZED", 401, "Unauthorized")
            claims = jwt.decode(
                token,
                key,
                algorithms=["RS256"],
                issuer=self.issuer,
                audience=self.audience,
                options={"require": ["exp", "iss", "aud", "sub"]},
            )
            sub = claims.get("sub")
            raw_scope = claims.get("scope")
            if (
                not isinstance(sub, str)
                or not sub
                or not isinstance(raw_scope, str)
                or any(not item for item in raw_scope.split(" "))
            ):
                raise RuntimeFailure("UNAUTHORIZED", 401, "Unauthorized")
            return Principal(sub=sub, scopes=frozenset(raw_scope.split(" ")))
        except RuntimeFailure:
            raise
        except jwt.PyJWTError as exc:
            raise RuntimeFailure("UNAUTHORIZED", 401, "Unauthorized") from exc


class CursorCodec:
    def __init__(self, pepper: bytes) -> None:
        self.pepper = pepper

    def encode(self, payload: dict[str, Any]) -> str:
        raw = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
        signature = hmac.new(self.pepper, raw, hashlib.sha256).digest()
        return base64.urlsafe_b64encode(raw + signature).rstrip(b"=").decode()

    def decode(self, value: str) -> dict[str, Any]:
        try:
            padding = "=" * (-len(value) % 4)
            data = base64.b64decode(
                value + padding, altchars=b"-_", validate=True
            )
            if len(data) <= 32:
                raise ValueError
            raw, signature = data[:-32], data[-32:]
            expected = hmac.new(self.pepper, raw, hashlib.sha256).digest()
            if not hmac.compare_digest(signature, expected):
                raise ValueError
            payload = json.loads(raw)
            if not isinstance(payload, dict) or json.dumps(
                payload, sort_keys=True, separators=(",", ":")
            ).encode() != raw:
                raise ValueError
            return payload
        except (ValueError, TypeError, json.JSONDecodeError, UnicodeDecodeError) as exc:
            raise RuntimeFailure("BAD_REQUEST", 400, "Bad request") from exc


class RateLimiter:
    """Single-replica in-memory sliding-window limiter."""

    def __init__(self, now: Callable[[], float] = time.monotonic) -> None:
        self.now = now
        self._events: dict[tuple[str, str], deque[float]] = defaultdict(deque)
        self._lock = threading.Lock()

    def check(self, actor: str, category: str, limit: int) -> None:
        current = self.now()
        key = (actor, category)
        with self._lock:
            events = self._events[key]
            while events and events[0] <= current - 60:
                events.popleft()
            if len(events) >= limit:
                raise RuntimeFailure("RATE_LIMITED", 429, "Too many requests")
            events.append(current)

