from pydantic import BaseModel


class OverviewResponse(BaseModel):
    totalPrompts: int
    totalReviews: int
    averageScore: float | None
    promptsWithAttachment: int
    mostUsedTag: str | None
    lastRefreshedAt: str | None
    lastRefreshError: str | None


class TrendBucket(BaseModel):
    period: str
    count: int


class TrendsResponse(BaseModel):
    interval: str
    days: int
    promptsCreated: list[TrendBucket]
    reviewsSubmitted: list[TrendBucket]


class TagPerformance(BaseModel):
    tag: str
    promptCount: int
    averageScore: float | None


class ReviewerRanking(BaseModel):
    reviewerName: str
    reviewCount: int


class PromptRanking(BaseModel):
    promptId: str
    name: str | None
    averageScore: float
    reviewCount: int


class LeaderboardResponse(BaseModel):
    minimumReviewsForRanking: int
    topReviewers: list[ReviewerRanking]
    topPrompts: list[PromptRanking]
    bottomPrompts: list[PromptRanking]


class CorrelationResponse(BaseModel):
    correlationCoefficient: float | None
    sampleSize: int
    caveat: str


class ErrorResponse(BaseModel):
    """Matches the shape prompt-service/review-service already return."""

    timestamp: str
    status: int
    error: str
    message: str
