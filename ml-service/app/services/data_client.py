"""
Pulls the FULL set of prompts and reviews from prompt-service/review-service,
paging through Week 2's Pageable-based list endpoints rather than assuming
everything fits on one page.

ml-service has no database of its own - this module is the only place data
enters the service, and it always comes over HTTP, never by reading another
service's storage directly. Same rule Week 3 set for analytics-service.

Unlike analytics-service, ml-service works item-by-item (one embedding per
prompt, one sentiment call per review) rather than as bulk pandas
aggregation, so this returns plain lists of dicts instead of DataFrames.
"""

import logging

from app.core.config import get_settings
from app.services.auth_client import authenticated_request

log = logging.getLogger("ml_service.data_client")

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


async def fetch_prompts() -> list[dict]:
    """Each item has at least: id, name, content, updatedAt (see Prompt entity)."""
    settings = get_settings()
    return await _fetch_all_pages(f"{settings.prompt_service_url}/prompts")


async def fetch_reviews() -> list[dict]:
    """Each item has at least: id, score, feedback (see Review model)."""
    settings = get_settings()
    return await _fetch_all_pages(f"{settings.review_service_url}/reviews")
