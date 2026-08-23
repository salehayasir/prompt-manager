"""
Thin wrapper around the Hugging Face Inference API (the free, hosted,
serverless "hf-inference" provider, reached through Hugging Face's router:
https://router.huggingface.co/hf-inference/models/{model_id}).

NOTE ON API STABILITY: Hugging Face has moved Inference API traffic behind a
routing layer ("Inference Providers") over the last couple of years, and the
exact base URL has shifted more than once. As the assignment itself warns,
"API details do shift over time" - if this stops working, check
https://huggingface.co/docs/inference-providers for the current base URL and
update HF_ROUTER_BASE below; nothing else in this module should need to
change, since the request/response shapes for the classic pipeline tasks
(feature-extraction, text-classification) have stayed the same.

This module deliberately does its OWN retry/backoff instead of using the
huggingface_hub client's built-in retry, so the cold-start-vs-failure
distinction the assignment asks for is visible in this code rather than
hidden inside a library.

Three distinct outcomes, three distinct exceptions - the same 404-vs-503
discipline Week 1 asked for:
  - HFModelLoadingError: the model is warming up (HTTP 503 + a "loading"
    style body). Transient. Worth retrying after a short wait.
  - HFRateLimitedError: HTTP 429. Also transient, but retrying immediately
    within the same call won't help - the caller should stop calling HF for
    the rest of this refresh cycle and try again next cycle.
  - HFPermanentError: anything else that isn't a 2xx. Not worth retrying.
"""

import asyncio
import logging

import httpx
import numpy as np

from app.core.config import get_settings

log = logging.getLogger("ml_service.hf_client")

HF_ROUTER_BASE = "https://router.huggingface.co/hf-inference/models"

# Cold starts are documented as 30-60s. Wait for the API's own estimate when
# it gives one, otherwise fall back to this. Never wait longer than this cap
# in a single attempt, and never retry more than MAX_COLD_START_RETRIES times
# - past that, the refresh loop should move on and pick this item up next
# scheduled cycle rather than block everything else on one stuck model.
DEFAULT_COLD_START_WAIT_SEC = 20.0
MAX_COLD_START_WAIT_SEC = 65.0
MAX_COLD_START_RETRIES = 2


class HFModelLoadingError(Exception):
    """Model is still warming up after all retries were exhausted."""


class HFRateLimitedError(Exception):
    """HTTP 429 - the free tier's hourly quota has been hit."""


class HFPermanentError(Exception):
    """Any other non-2xx response. Retrying this exact call won't help."""


async def _call_model(model_id: str, payload: dict) -> dict | list:
    """
    POSTs to the Inference API for a given model, handling cold starts by
    retrying and everything else by raising the exception that matches.
    """
    settings = get_settings()
    url = f"{HF_ROUTER_BASE}/{model_id}"
    headers = {"Authorization": f"Bearer {settings.huggingface_api_token}"}

    attempt = 0
    while True:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(url, headers=headers, json=payload)

        if response.status_code == 200:
            return response.json()

        if response.status_code == 503:
            # Distinguish "still loading" from a genuine 503 server error by
            # looking at the body, not just the status code - a model that's
            # permanently gone can also 503, and we don't want to loop on that.
            body = _safe_json(response)
            is_loading = isinstance(body, dict) and (
                "loading" in str(body.get("error", "")).lower()
                or "estimated_time" in body
            )

            if not is_loading:
                raise HFPermanentError(
                    f"{model_id} returned 503 without a 'loading' body: {body}"
                )

            if attempt >= MAX_COLD_START_RETRIES:
                raise HFModelLoadingError(
                    f"{model_id} still loading after {attempt + 1} attempts, giving up "
                    "for this refresh cycle"
                )

            wait_sec = min(
                float(body.get("estimated_time", DEFAULT_COLD_START_WAIT_SEC)) + 1.0,
                MAX_COLD_START_WAIT_SEC,
            )
            log.info(
                "%s is cold-starting (attempt %d/%d) - waiting %.1fs before retrying",
                model_id, attempt + 1, MAX_COLD_START_RETRIES + 1, wait_sec,
            )
            await asyncio.sleep(wait_sec)
            attempt += 1
            continue

        if response.status_code == 429:
            raise HFRateLimitedError(
                f"{model_id} rate limited (HTTP 429) - back off until the next refresh cycle"
            )

        # Any other status (400, 401, 403, 404, 500...) is a real failure,
        # not a "try again" situation.
        raise HFPermanentError(
            f"{model_id} returned HTTP {response.status_code}: {_safe_json(response)}"
        )


def _safe_json(response: httpx.Response):
    try:
        return response.json()
    except ValueError:
        return response.text


def _to_sentence_vector(raw) -> list[float]:
    """
    Normalizes whatever shape the feature-extraction endpoint hands back into
    a single flat sentence vector.

    sentence-transformers models can come back as:
      - a flat list[float]                    (already a sentence embedding)
      - a list[list[float]]                   (per-token vectors -> mean pool)
      - a list[list[list[float]]] of length 1 (batched form of the above)
    """
    arr = np.array(raw, dtype=float)

    if arr.ndim == 1:
        return arr.tolist()
    if arr.ndim == 2:
        return arr.mean(axis=0).tolist()
    if arr.ndim == 3 and arr.shape[0] == 1:
        return arr[0].mean(axis=0).tolist()

    raise HFPermanentError(f"Unexpected embedding response shape: {arr.shape}")


async def get_embedding(text: str) -> list[float]:
    settings = get_settings()
    raw = await _call_model(
        f"{settings.hf_embedding_model}/pipeline/feature-extraction",
        {"inputs": text},
    )
    return _to_sentence_vector(raw)


async def get_sentiment(text: str) -> tuple[str, float]:
    """
    Returns (label, confidence) for the single most likely label, e.g.
    ("POSITIVE", 0.987). The sentiment endpoint can return either a flat
    list of {label, score} dicts, or a batched (list-of-list) form for a
    single input - both are handled.
    """
    settings = get_settings()
    raw = await _call_model(settings.hf_sentiment_model, {"inputs": text})

    scores = raw[0] if raw and isinstance(raw[0], list) else raw
    if not scores:
        raise HFPermanentError(f"Sentiment model returned an empty result: {raw}")

    best = max(scores, key=lambda item: item["score"])
    return best["label"], float(best["score"])
