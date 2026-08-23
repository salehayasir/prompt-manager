"""
Owns the two things ml-service keeps in memory:
  - one embedding vector per prompt (for semantic search)
  - one sentiment result per review (for the review quality check)

Also owns the incremental refresh logic from Part 5: on each scheduled run,
only prompts/reviews that are new or changed get sent to the Hugging Face
API. Everything else reuses the vector/sentiment it already has, which is
what keeps this service inside the free tier's rate limit.

Mirrors analytics-service's SnapshotStore in spirit (background refresh,
requests read a cheap in-memory result) but CANNOT just discard-and-rebuild
like that store does, because the whole point here is to remember what was
already computed so it doesn't get recomputed.
"""

import logging
from dataclasses import dataclass
from datetime import datetime, timezone

from app.core.config import get_settings
from app.services.data_client import fetch_prompts, fetch_reviews
from app.services.hf_client import (
    HFModelLoadingError,
    HFPermanentError,
    HFRateLimitedError,
    get_embedding,
    get_sentiment,
)

log = logging.getLogger("ml_service.store")


@dataclass
class PromptVectorEntry:
    id: str
    name: str | None
    content: str
    updated_at: str | None
    vector: list[float]


@dataclass
class ReviewQualityEntry:
    id: str
    prompt_id: str | None
    reviewer_name: str | None
    score: int | None
    feedback: str
    sentiment_label: str
    sentiment_confidence: float
    expected_sentiment: str
    flagged: bool
    reason: str | None


def _expected_sentiment(score: int | None) -> str:
    """
    Score -> expected sentiment direction rule (documented in README):
      4 or 5  -> expected positive
      1 or 2  -> expected negative
      3       -> neutral / no expectation, never flagged - a middling score
                 paired with any sentiment isn't really a contradiction.
    """
    if score is None:
        return "neutral"
    if score >= 4:
        return "positive"
    if score <= 2:
        return "negative"
    return "neutral"


def _evaluate_flag(
    expected: str, label: str, confidence: float, score: int | None, threshold: float
) -> tuple[bool, str | None]:
    """
    Flags only when BOTH: the model's sentiment disagrees with the direction
    implied by the score, AND the model is confident enough about it. A low
    confidence disagreement is weak evidence and is deliberately left
    unflagged rather than treated the same as a confident one.
    """
    if expected == "neutral":
        return False, None

    model_direction = "positive" if label.upper().startswith("POS") else "negative"
    if model_direction == expected:
        return False, None

    if confidence < threshold:
        return False, None

    reason = (
        f"Score of {score}/5 suggests {expected} feedback, but the model "
        f"detected {label} sentiment with {confidence:.0%} confidence."
    )
    return True, reason


class MLStore:
    def __init__(self):
        self.prompt_vectors: dict[str, PromptVectorEntry] = {}
        self.review_quality: dict[str, ReviewQualityEntry] = {}
        self.last_refreshed_at: datetime | None = None
        self.last_refresh_error: str | None = None
        self.last_refresh_stats: dict = {}

    @property
    def has_data(self) -> bool:
        return self.last_refreshed_at is not None

    async def refresh(self) -> None:
        settings = get_settings()

        try:
            prompts = await fetch_prompts()
            reviews = await fetch_reviews()
        except Exception as exc:  # noqa: BLE001
            log.error(
                "Could not reach prompt-service/review-service, keeping "
                "previous ml-service data: %s", exc,
            )
            self.last_refresh_error = str(exc)
            return

        embed_stats = await self._refresh_prompt_vectors(prompts)
        sentiment_stats = await self._refresh_review_quality(reviews, settings)

        self.last_refreshed_at = datetime.now(timezone.utc)
        self.last_refresh_error = None
        self.last_refresh_stats = {"embeddings": embed_stats, "sentiment": sentiment_stats}
        log.info(
            "ml-service refresh complete: %d prompt vectors, %d review "
            "quality entries (embed calls=%d, sentiment calls=%d)",
            len(self.prompt_vectors), len(self.review_quality),
            embed_stats["api_calls"], sentiment_stats["api_calls"],
        )

    async def _refresh_prompt_vectors(self, prompts: list[dict]) -> dict:
        stats = {"api_calls": 0, "reused": 0, "skipped_rate_limited": 0, "errors": 0}
        rate_limited = False

        for prompt in prompts:
            pid = str(prompt.get("id"))
            content = prompt.get("content") or ""
            updated_at = prompt.get("updatedAt")

            existing = self.prompt_vectors.get(pid)
            unchanged = (
                existing is not None
                and existing.content == content
                and existing.updated_at == updated_at
            )
            if unchanged:
                stats["reused"] += 1
                continue

            if rate_limited:
                # Already hit the rate limit this cycle - don't keep trying,
                # just leave this one for the next scheduled refresh.
                stats["skipped_rate_limited"] += 1
                continue

            try:
                vector = await get_embedding(content)
                stats["api_calls"] += 1
            except HFRateLimitedError as exc:
                log.warning("Embedding rate limited, deferring remaining prompts: %s", exc)
                rate_limited = True
                stats["skipped_rate_limited"] += 1
                continue
            except (HFModelLoadingError, HFPermanentError) as exc:
                log.warning("Could not embed prompt %s this cycle: %s", pid, exc)
                stats["errors"] += 1
                continue

            self.prompt_vectors[pid] = PromptVectorEntry(
                id=pid, name=prompt.get("name"), content=content,
                updated_at=updated_at, vector=vector,
            )

        # Drop vectors for prompts that were deleted upstream.
        current_ids = {str(p.get("id")) for p in prompts}
        for stale_id in [pid for pid in self.prompt_vectors if pid not in current_ids]:
            del self.prompt_vectors[stale_id]

        return stats

    async def _refresh_review_quality(self, reviews: list[dict], settings) -> dict:
        stats = {"api_calls": 0, "reused": 0, "skipped_rate_limited": 0, "errors": 0}
        rate_limited = False

        for review in reviews:
            rid = str(review.get("id"))
            feedback = (review.get("feedback") or "").strip()
            score = review.get("score")

            if not feedback:
                # No text to analyze - drop any stale entry and move on.
                self.review_quality.pop(rid, None)
                continue

            existing = self.review_quality.get(rid)
            unchanged = (
                existing is not None
                and existing.feedback == feedback
                and existing.score == score
            )
            if unchanged:
                stats["reused"] += 1
                continue

            if rate_limited:
                stats["skipped_rate_limited"] += 1
                continue

            try:
                label, confidence = await get_sentiment(feedback)
                stats["api_calls"] += 1
            except HFRateLimitedError as exc:
                log.warning("Sentiment rate limited, deferring remaining reviews: %s", exc)
                rate_limited = True
                stats["skipped_rate_limited"] += 1
                continue
            except (HFModelLoadingError, HFPermanentError) as exc:
                log.warning("Could not score review %s this cycle: %s", rid, exc)
                stats["errors"] += 1
                continue

            expected = _expected_sentiment(score)
            flagged, reason = _evaluate_flag(
                expected, label, confidence, score, settings.sentiment_confidence_threshold,
            )

            self.review_quality[rid] = ReviewQualityEntry(
                id=rid,
                prompt_id=str(review.get("promptId")) if review.get("promptId") else None,
                reviewer_name=review.get("reviewerName"),
                score=score,
                feedback=feedback,
                sentiment_label=label,
                sentiment_confidence=confidence,
                expected_sentiment=expected,
                flagged=flagged,
                reason=reason,
            )

        current_ids = {str(r.get("id")) for r in reviews}
        for stale_id in [rid for rid in self.review_quality if rid not in current_ids]:
            del self.review_quality[stale_id]

        return stats


ml_store = MLStore()
