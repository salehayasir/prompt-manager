import logging

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse

from app.core.config import get_settings
from app.routers import health, ml
from app.services.auth_client import token_store
from app.services.store import ml_store

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("ml_service")

app = FastAPI(title="ml-service", version="1.0.0")

app.include_router(health.router)
app.include_router(ml.router)

scheduler = AsyncIOScheduler()


@app.exception_handler(HTTPException)
async def http_exception_handler(request, exc: HTTPException):
    """
    verify_token() raises HTTPException with a pre-shaped error body in
    `detail` (timestamp/status/error/message). FastAPI's default handler
    would wrap that as {"detail": {...}}; this unwraps it so a 401 from
    ml-service is byte-for-byte the same shape the other three services
    already return. Other HTTPExceptions (e.g. the 404/429/503/502 raised
    from routers/ml.py) get a plain {status, message} body.
    """
    if isinstance(exc.detail, dict) and "status" in exc.detail:
        return JSONResponse(status_code=exc.status_code, content=exc.detail)

    return JSONResponse(
        status_code=exc.status_code,
        content={"status": exc.status_code, "message": str(exc.detail)},
    )


@app.on_event("startup")
async def on_startup():
    settings = get_settings()
    log.info(
        "ml-service starting on port %s, refresh interval %ss",
        settings.ml_service_port,
        settings.ml_refresh_interval_sec,
    )

    # Log in once at startup. If prompt-service isn't up yet, don't crash the
    # whole app - the scheduled refresh below will keep retrying, and
    # get_or_login() will retry on the next call anyway.
    try:
        await token_store.login()
    except Exception as exc:  # noqa: BLE001
        log.warning(
            "Initial login to prompt-service failed (%s) - "
            "will retry on the next scheduled refresh",
            exc,
        )

    # Populate the store once immediately, so endpoints aren't empty before
    # the first scheduled interval elapses. This first run is also the most
    # expensive one, since nothing is cached yet - every prompt and every
    # reviewed piece of feedback needs a Hugging Face call.
    await ml_store.refresh()

    scheduler.add_job(
        ml_store.refresh,
        "interval",
        seconds=settings.ml_refresh_interval_sec,
        id="ml_refresh",
    )
    scheduler.start()


@app.on_event("shutdown")
async def on_shutdown():
    scheduler.shutdown(wait=False)
