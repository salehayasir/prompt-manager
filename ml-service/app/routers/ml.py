import logging

from fastapi import APIRouter, Depends, HTTPException, Query, status

from app.core.config import get_settings
from app.core.security import verify_token
from app.schemas.ml import (
    ReviewQualityItem,
    ReviewQualityResponse,
    SearchResponse,
    SearchResult,
    SimilarResponse,
)
from app.services.hf_client import (
    HFModelLoadingError,
    HFPermanentError,
    HFRateLimitedError,
    get_embedding,
)
from app.services.similarity import top_k_by_similarity
from app.services.store import ml_store

log = logging.getLogger("ml_service.router")

router = APIRouter(prefix="/ml", tags=["ml"], dependencies=[Depends(verify_token)])


@router.get("/search", response_model=SearchResponse)
async def semantic_search(
    q: str = Query(..., min_length=1, description="Free-text search query"),
    top_k: int = Query(default=5, ge=1, le=50),
):
    """
    Embeds the query text with a fresh call to the embedding model, then
    ranks every stored prompt vector by cosine similarity. This is the one
    search path that always costs one Hugging Face call, since a novel query
    string can never be reused from the cache the way prompt content is.
    """
    try:
        query_vector = await get_embedding(q)
    except HFRateLimitedError as exc:
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, str(exc)) from exc
    except HFModelLoadingError as exc:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, str(exc)) from exc
    except HFPermanentError as exc:
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, str(exc)) from exc

    candidates = {pid: entry.vector for pid, entry in ml_store.prompt_vectors.items()}
    ranked = top_k_by_similarity(query_vector, candidates, top_k)

    results = [
        SearchResult(
            promptId=pid,
            name=ml_store.prompt_vectors[pid].name,
            score=round(score, 4),
        )
        for pid, score in ranked
    ]
    return SearchResponse(query=q, results=results)


@router.get("/prompts/{prompt_id}/similar", response_model=SimilarResponse)
async def similar_prompts(
    prompt_id: str,
    top_k: int = Query(default=5, ge=1, le=50),
):
    """
    Same idea as /search, but the query is an existing prompt's own stored
    vector, so it costs zero Hugging Face calls - the embedding was already
    computed during the last refresh.
    """
    entry = ml_store.prompt_vectors.get(prompt_id)
    if entry is None:
        raise HTTPException(
            status.HTTP_404_NOT_FOUND,
            f"No stored embedding for prompt {prompt_id} - it may not exist, "
            "or hasn't been embedded yet by the background refresh",
        )

    candidates = {pid: e.vector for pid, e in ml_store.prompt_vectors.items()}
    ranked = top_k_by_similarity(entry.vector, candidates, top_k, exclude_id=prompt_id)

    results = [
        SearchResult(
            promptId=pid,
            name=ml_store.prompt_vectors[pid].name,
            score=round(score, 4),
        )
        for pid, score in ranked
    ]
    return SimilarResponse(promptId=prompt_id, results=results)


@router.get("/review-quality", response_model=ReviewQualityResponse)
def review_quality():
    """
    Every review with non-empty feedback, its sentiment label/confidence,
    whether it was flagged, and why. Flagged reviews sort first.
    """
    settings = get_settings()
    entries = list(ml_store.review_quality.values())
    entries.sort(key=lambda e: (not e.flagged, e.id))

    items = [
        ReviewQualityItem(
            reviewId=e.id,
            promptId=e.prompt_id,
            reviewerName=e.reviewer_name,
            score=e.score,
            feedback=e.feedback,
            sentimentLabel=e.sentiment_label,
            sentimentConfidence=round(e.sentiment_confidence, 4),
            expectedSentiment=e.expected_sentiment,
            flagged=e.flagged,
            reason=e.reason,
        )
        for e in entries
    ]

    return ReviewQualityResponse(
        lastRefreshedAt=ml_store.last_refreshed_at.isoformat() if ml_store.last_refreshed_at else None,
        lastRefreshError=ml_store.last_refresh_error,
        confidenceThreshold=settings.sentiment_confidence_threshold,
        totalReviewsAnalyzed=len(items),
        flaggedCount=sum(1 for i in items if i.flagged),
        reviews=items,
    )
