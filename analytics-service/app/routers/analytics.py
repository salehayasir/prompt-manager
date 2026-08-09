from fastapi import APIRouter, Depends, Query

from app.core.security import verify_token
from app.schemas.analytics import (
    CorrelationResponse,
    LeaderboardResponse,
    OverviewResponse,
    TagPerformance,
    TrendsResponse,
)
from app.services import analytics
from app.services.snapshot import snapshot_store

router = APIRouter(
    prefix="/analytics",
    tags=["analytics"],
    dependencies=[Depends(verify_token)],
)


@router.get("/overview", response_model=OverviewResponse)
def get_overview():
    return analytics.compute_overview(snapshot_store.current)


@router.get("/trends", response_model=TrendsResponse)
def get_trends(
    interval: str = Query(default="day", pattern="^(day|week)$"),
    days: int = Query(default=30, ge=1, le=365),
):
    return analytics.compute_trends(snapshot_store.current, interval, days)


@router.get("/tags", response_model=list[TagPerformance])
def get_tag_performance():
    return analytics.compute_tag_performance(snapshot_store.current)


@router.get("/leaderboard", response_model=LeaderboardResponse)
def get_leaderboard():
    return analytics.compute_leaderboard(snapshot_store.current)


@router.get("/correlation", response_model=CorrelationResponse)
def get_correlation():
    return analytics.compute_correlation(snapshot_store.current)
