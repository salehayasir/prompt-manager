"""
analytics-service acting as a CLIENT of prompt-service's /auth/login.

This is the second, separate JWT problem from core/security.py: rather than
verifying someone else's token, this module gets analytics-service its own
token so it's allowed to call prompt-service and review-service (both are
JWT-protected as of Week 2).

Known simplification (flagged in README, per the assignment's instruction
not to quietly present this as ideal design): this reuses the single human
login account from Week 2 rather than a truly separate machine-account
mechanism. A real system would not have a background service share
credentials with a human user.
"""

import logging
import threading

import httpx

from app.core.config import get_settings

log = logging.getLogger("analytics_service.auth")


class TokenStore:
    """
    Holds the current token in memory and knows how to (re)acquire it.
    Thread-safe with a simple lock since APScheduler and FastAPI request
    handlers may both trigger a refresh concurrently.
    """

    def __init__(self):
        self._token: str | None = None
        self._lock = threading.Lock()

    @property
    def token(self) -> str | None:
        return self._token

    async def login(self) -> str:
        """
        Calls prompt-service's shared login endpoint with analytics-service's
        own dedicated credentials, and caches the resulting token.
        """
        settings = get_settings()

        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.post(
                f"{settings.prompt_service_url}/auth/login",
                json={
                    "username": settings.analytics_service_username,
                    "password": settings.analytics_service_password,
                },
            )
            response.raise_for_status()
            token = response.json()["token"]

        with self._lock:
            self._token = token

        log.info("analytics-service authenticated with prompt-service")
        return token

    async def get_or_login(self) -> str:
        """Returns the cached token, logging in for the first time if needed."""
        if self._token is None:
            return await self.login()
        return self._token


token_store = TokenStore()


async def authenticated_request(method: str, url: str, **kwargs) -> httpx.Response:
    """
    Makes an HTTP call with the current token attached. On a 401 (token
    expired/rejected), logs in again ONCE and retries - per the spec, this
    is a signal to re-authenticate and retry once, not to fail outright.
    """
    token = await token_store.get_or_login()

    async with httpx.AsyncClient(timeout=10.0) as client:
        headers = {"Authorization": f"Bearer {token}"}
        response = await client.request(method, url, headers=headers, **kwargs)

        if response.status_code == 401:
            log.warning("Got 401 calling %s - re-authenticating and retrying once", url)
            token = await token_store.login()
            headers = {"Authorization": f"Bearer {token}"}
            response = await client.request(method, url, headers=headers, **kwargs)

        return response
