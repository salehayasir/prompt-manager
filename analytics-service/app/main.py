import logging
from datetime import datetime, timezone

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from fastapi import FastAPI, HTTPException, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.core.config import get_settings
from app.routers import analytics, health
from app.services.auth_client import token_store
from app.services.snapshot import snapshot_store

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("analytics_service")

app = FastAPI(title="analytics-service", version="1.0.0")

app.include_router(health.router)
app.include_router(analytics.router)

scheduler = AsyncIOScheduler()


@app.exception_handler(HTTPException)
async def http_exception_handler(request, exc: HTTPException):
    """
    verify_token() raises HTTPException with a pre-shaped error body in
    `detail` (timestamp/status/error/message). FastAPI's default handler
    would wrap that as {"detail": {...}}; this unwraps it so a 401 from
    analytics-service is byte-for-byte the same shape prompt-service and
    review-service already return.
    """
    if isinstance(exc.detail, dict) and "status" in exc.detail:
        return JSONResponse(status_code=exc.status_code, content=exc.detail)

    return JSONResponse(
        status_code=exc.status_code,
        content={"status": exc.status_code, "message": str(exc.detail)},
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request, exc: RequestValidationError):
    """
    Query/body validation failures (e.g. /analytics/trends?interval=foo, or
    ?days=9999 exceeding the le=365 bound) raise RequestValidationError, NOT
    HTTPException - so without this handler they'd fall through to FastAPI's
    default {"detail": [...]} array shape instead of matching the
    {timestamp, status, error, message} convention every other error in
    this app (and prompt-service/review-service) follows.
    """
    first_error = exc.errors()[0] if exc.errors() else {}
    field = ".".join(str(loc) for loc in first_error.get("loc", []) if loc != "query")
    message = first_error.get("msg", "Invalid request parameters")

    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "status": status.HTTP_400_BAD_REQUEST,
            "error": "Bad Request",
            "message": f"{field}: {message}" if field else message,
        },
    )


@app.on_event("startup")
async def on_startup():
    settings = get_settings()
    log.info(
        "analytics-service starting on port %s, refresh interval %ss",
        settings.analytics_service_port,
        settings.analytics_refresh_interval_sec,
    )

    # Log in once at startup, as the spec requires. If prompt-service isn't
    # up yet, don't crash the whole app - the scheduled refresh below will
    # keep retrying, and get_or_login() will retry on the next call anyway.
    try:
        await token_store.login()
    except Exception as exc:  # noqa: BLE001
        log.warning(
            "Initial login to prompt-service failed (%s) - "
            "will retry on the next scheduled refresh",
            exc,
        )

    # Populate the snapshot once immediately, so endpoints aren't empty
    # before the first scheduled interval elapses (same idea as Week 2's
    # digest job computing once at startup via @PostConstruct).
    await snapshot_store.refresh()

    scheduler.add_job(
        snapshot_store.refresh,
        "interval",
        seconds=settings.analytics_refresh_interval_sec,
        id="snapshot_refresh",
    )
    scheduler.start()


@app.on_event("shutdown")
async def on_shutdown():
    scheduler.shutdown(wait=False)
