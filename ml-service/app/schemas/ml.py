from pydantic import BaseModel


class SearchResult(BaseModel):
    promptId: str
    name: str | None
    score: float


class SearchResponse(BaseModel):
    query: str
    results: list[SearchResult]


class SimilarResponse(BaseModel):
    promptId: str
    results: list[SearchResult]


class ReviewQualityItem(BaseModel):
    reviewId: str
    promptId: str | None
    reviewerName: str | None
    score: int | None
    feedback: str
    sentimentLabel: str
    sentimentConfidence: float
    expectedSentiment: str
    flagged: bool
    reason: str | None


class ReviewQualityResponse(BaseModel):
    lastRefreshedAt: str | None
    lastRefreshError: str | None
    confidenceThreshold: float
    totalReviewsAnalyzed: int
    flaggedCount: int
    reviews: list[ReviewQualityItem]


class ErrorResponse(BaseModel):
    """Matches the shape prompt-service/review-service/analytics-service already return."""

    timestamp: str
    status: int
    error: str
    message: str
