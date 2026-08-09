"""
Pulls the FULL set of prompts and reviews from prompt-service/review-service,
paging through Week 2's Pageable-based list endpoints rather than assuming
everything fits on one page.

analytics-service has no database of its own - this module is the only
place data enters the service, and it always comes over HTTP, never by
reading another service's storage directly.
"""

import logging

import pandas as pd

from app.core.config import get_settings
from app.services.auth_client import authenticated_request

log = logging.getLogger("analytics_service.data_client")

PAGE_SIZE = 100  # large page size keeps the number of requests small


async def _fetch_all_pages(base_url: str) -> list[dict]:
    """
    Pages through a Week 2-style paginated endpoint
    (content/totalPages/currentPage/...) until every page is collected.
    """
    all_items: list[dict] = []
    page = 0

    while True:
        response = await authenticated_request(
            "GET",
            base_url,
            params={"page": page, "size": PAGE_SIZE},
        )
        response.raise_for_status()
        body = response.json()

        all_items.extend(body.get("content", []))

        total_pages = body.get("totalPages", 1)
        page += 1
        if page >= total_pages:
            break

    return all_items


async def fetch_prompts_dataframe() -> pd.DataFrame:
    settings = get_settings()
    items = await _fetch_all_pages(f"{settings.prompt_service_url}/prompts")

    df = pd.DataFrame(items)
    if df.empty:
        # Keep a consistent, typed empty frame so downstream pandas
        # operations (groupby, merge) don't blow up on missing columns.
        df = pd.DataFrame(
            columns=[
                "id", "name", "description", "content", "tags",
                "modelTarget", "attachmentUrl", "attachmentPublicId",
                "createdAt", "updatedAt",
            ]
        )

    for col in ("createdAt", "updatedAt"):
        if col in df.columns:
            df[col] = pd.to_datetime(df[col], errors="coerce")

    if "content" in df.columns:
        df["content_length"] = df["content"].fillna("").str.len()
    else:
        df["content_length"] = pd.Series(dtype="int64")

    return df


async def fetch_reviews_dataframe() -> pd.DataFrame:
    settings = get_settings()
    items = await _fetch_all_pages(f"{settings.review_service_url}/reviews")

    df = pd.DataFrame(items)
    if df.empty:
        # Field names match review-service's Review model exactly:
        # id, promptId, promptSnapshot, reviewerName, score, feedback, reviewedAt
        df = pd.DataFrame(
            columns=[
                "id", "promptId", "promptSnapshot", "reviewerName",
                "score", "feedback", "reviewedAt",
            ]
        )

    if "reviewedAt" in df.columns:
        df["reviewedAt"] = pd.to_datetime(df["reviewedAt"], errors="coerce")

    if "score" in df.columns:
        df["score"] = pd.to_numeric(df["score"], errors="coerce")

    return df
