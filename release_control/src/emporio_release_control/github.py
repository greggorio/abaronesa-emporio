"""Bounded GitHub App transport with immutable repository authority."""

from __future__ import annotations

import re
import time
from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime
from typing import Any

import httpx
import jwt

from .constants import (
    DEPLOYER_WORKFLOW,
    OWNER,
    PUBLISHER_WORKFLOW,
    REF,
    REPO,
    REPOSITORY,
    ROLLBACK_WORKFLOW,
    USER_AGENT,
)
from .errors import (
    PreDispatchFailure,
    RemoteHttpFailure,
    RemoteTransportFailure,
    RuntimeFailure,
)

MAX_JSON_BYTES = 4 * 1024 * 1024
MAX_BINARY_BYTES = 16 * 1024 * 1024
REDIRECT_STATUSES = frozenset({301, 302, 303, 307, 308})


@dataclass(slots=True)
class InstallationToken:
    value: str
    expires_at: float


class GitHubClient:
    def __init__(
        self,
        *,
        app_id: int,
        installation_id: int,
        private_key: bytes,
        api_base: str,
        connect_timeout: int,
        read_timeout: int,
        max_pages: int,
        client: httpx.Client | None = None,
        now: Callable[[], float] = time.time,
    ) -> None:
        self.app_id = app_id
        self.installation_id = installation_id
        self.private_key = private_key
        self.api_base = api_base.rstrip("/")
        self.max_pages = max_pages
        self.now = now
        self.client = client or httpx.Client(
            timeout=httpx.Timeout(read_timeout, connect=connect_timeout),
            follow_redirects=False,
        )
        self._token: InstallationToken | None = None

    @property
    def fixed_headers(self) -> dict[str, str]:
        return {
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2026-03-10",
            "User-Agent": USER_AGENT,
        }

    def _app_jwt(self) -> str:
        now = int(self.now())
        encoded = jwt.encode(
            {"iat": now - 60, "exp": now + 540, "iss": str(self.app_id)},
            self.private_key,
            algorithm="RS256",
        )
        return str(encoded)

    def _installation_token(self, force: bool = False) -> str:
        if (
            not force
            and self._token is not None
            and self._token.expires_at - 60 > self.now()
        ):
            return self._token.value
        headers = {
            **self.fixed_headers,
            "Authorization": f"Bearer {self._app_jwt()}",
        }
        endpoint = f"{self.api_base}/app/installations/{self.installation_id}/access_tokens"
        try:
            response = self.client.post(endpoint, headers=headers, content=b"{}")
        except httpx.HTTPError as exc:
            raise RemoteTransportFailure() from exc
        if response.status_code != 201:
            raise RemoteHttpFailure(response.status_code)
        value = self._json(response, limit=64 * 1024)
        token = value.get("token")
        expires_at = value.get("expires_at")
        if not isinstance(token, str) or not token or not isinstance(expires_at, str):
            raise RuntimeFailure("GITHUB_RESPONSE_INVALID")
        try:
            expiry = datetime.fromisoformat(expires_at.replace("Z", "+00:00")).timestamp()
        except ValueError as exc:
            raise RuntimeFailure("GITHUB_RESPONSE_INVALID") from exc
        if expiry <= self.now() + 60:
            raise RuntimeFailure("GITHUB_RESPONSE_INVALID")
        self._token = InstallationToken(token, expiry)
        return token

    def _headers(self, force: bool = False) -> dict[str, str]:
        return {
            **self.fixed_headers,
            "Authorization": f"Bearer {self._installation_token(force)}",
        }

    @staticmethod
    def _json(response: httpx.Response, limit: int = MAX_JSON_BYTES) -> Any:
        if len(response.content) > limit:
            raise RuntimeFailure("GITHUB_RESPONSE_INVALID")
        try:
            value = response.json()
        except ValueError as exc:
            raise RuntimeFailure("GITHUB_RESPONSE_INVALID") from exc
        return value

    def get_json(self, path: str) -> dict[str, Any]:
        value = self.get_value(path)
        if not isinstance(value, dict):
            raise RuntimeFailure("GITHUB_RESPONSE_INVALID")
        return value

    def get_value(self, path: str) -> Any:
        if not path.startswith(f"/repos/{REPOSITORY}/"):
            raise RuntimeFailure("GITHUB_ENDPOINT_INVALID")
        for attempt in range(2):
            try:
                response = self.client.get(
                    f"{self.api_base}{path}", headers=self._headers(force=attempt == 1)
                )
            except httpx.HTTPError as exc:
                raise RemoteTransportFailure() from exc
            if response.status_code == 401 and attempt == 0:
                self._token = None
                continue
            if response.status_code != 200:
                raise RemoteHttpFailure(response.status_code)
            return self._json(response)
        raise RemoteHttpFailure(401)

    def get_bytes(self, path: str, limit: int = MAX_BINARY_BYTES) -> bytes:
        if not path.startswith(f"/repos/{REPOSITORY}/"):
            raise RuntimeFailure("GITHUB_ENDPOINT_INVALID")
        # Actions artifact download rejects application/octet-stream with 415;
        # release asset download requires it. Both answer with a signed redirect.
        accept = (
            "application/vnd.github+json"
            if path.endswith("/zip")
            else "application/octet-stream"
        )
        for attempt in range(2):
            try:
                response = self.client.get(
                    f"{self.api_base}{path}",
                    headers={**self._headers(force=attempt == 1), "Accept": accept},
                )
            except httpx.HTTPError as exc:
                raise RemoteTransportFailure() from exc
            if response.status_code == 401 and attempt == 0:
                self._token = None
                continue
            if response.status_code in REDIRECT_STATUSES:
                response = self._follow_signed_download(response)
            if response.status_code != 200:
                raise RemoteHttpFailure(response.status_code)
            if len(response.content) > limit:
                raise RuntimeFailure("GITHUB_RESPONSE_INVALID")
            return response.content
        raise RemoteHttpFailure(401)

    def _follow_signed_download(self, response: httpx.Response) -> httpx.Response:
        location = response.headers.get("location", "")
        # The target is pre-signed storage outside GitHub: never forward credentials.
        if not location.startswith("https://"):
            raise RuntimeFailure("GITHUB_RESPONSE_INVALID")
        try:
            return self.client.get(location, headers={"User-Agent": USER_AGENT})
        except httpx.HTTPError as exc:
            raise RemoteTransportFailure() from exc

    def list_pages(self, path: str, key: str | None) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        for page in range(1, self.max_pages + 1):
            separator = "&" if "?" in path else "?"
            value = self.get_value(f"{path}{separator}per_page=100&page={page}")
            items = value.get(key) if key is not None and isinstance(value, dict) else value
            if not isinstance(items, list) or any(not isinstance(item, dict) for item in items):
                raise RuntimeFailure("GITHUB_RESPONSE_INVALID")
            result.extend(items)
            if len(items) < 100:
                return result
        raise RuntimeFailure("GITHUB_PAGINATION_EXHAUSTED")

    def dispatch_publication(self, operation_id: str, request: dict[str, str]) -> None:
        endpoint = (
            f"{self.api_base}/repos/{OWNER}/{REPO}/actions/workflows/"
            f"{PUBLISHER_WORKFLOW}/dispatches"
        )
        payload = {
            "ref": REF,
            "inputs": {
                "operation_id": operation_id,
                "candidate_id": request["candidateId"],
                "version_bump": request["versionBump"],
                "description": request["description"],
                "changelog": request["changelog"],
            },
        }
        try:
            headers = self._headers()
        except Exception as exc:
            raise PreDispatchFailure() from exc
        try:
            response = self.client.post(endpoint, headers=headers, json=payload)
        except httpx.HTTPError as exc:
            raise RemoteTransportFailure(uncertain=True) from exc
        if response.status_code != 204:
            raise RemoteHttpFailure(response.status_code)

    def dispatch_deployment(self, operation_id: str, release: str) -> None:
        if re.fullmatch(r"dep_[0-9a-f]{32}", operation_id) is None or re.fullmatch(
            r"v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)",
            release,
        ) is None:
            raise PreDispatchFailure()
        endpoint = (
            f"{self.api_base}/repos/{OWNER}/{REPO}/actions/workflows/"
            f"{DEPLOYER_WORKFLOW}/dispatches"
        )
        payload = {
            "ref": REF,
            "inputs": {"operation_id": operation_id, "release": release},
        }
        try:
            headers = self._headers()
        except Exception as exc:
            raise PreDispatchFailure() from exc
        try:
            response = self.client.post(endpoint, headers=headers, json=payload)
        except httpx.HTTPError as exc:
            raise RemoteTransportFailure(uncertain=True) from exc
        if response.status_code != 204:
            raise RemoteHttpFailure(response.status_code)

    def dispatch_rollback(self, operation_id: str, release: str) -> None:
        if re.fullmatch(r"rbk_[0-9a-f]{32}", operation_id) is None or re.fullmatch(
            r"v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)",
            release,
        ) is None:
            raise PreDispatchFailure()
        endpoint = (
            f"{self.api_base}/repos/{OWNER}/{REPO}/actions/workflows/"
            f"{ROLLBACK_WORKFLOW}/dispatches"
        )
        payload = {
            "ref": REF,
            "inputs": {"operation_id": operation_id, "release": release},
        }
        try:
            headers = self._headers()
        except Exception as exc:
            raise PreDispatchFailure() from exc
        try:
            response = self.client.post(endpoint, headers=headers, json=payload)
        except httpx.HTTPError as exc:
            raise RemoteTransportFailure(uncertain=True) from exc
        if response.status_code != 204:
            raise RemoteHttpFailure(response.status_code)
