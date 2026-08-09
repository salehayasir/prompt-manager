"""
Owns the current in-memory snapshot: the latest prompts/reviews DataFrames,
plus the timestamp of the last successful refresh.

Analytics endpoints read from this snapshot rather than calling
prompt-service/review-service on every request - the same "expensive work
happens in the background, requests stay cheap" idea as Week 2's digest job.

If a refresh fails (either service unreachable), the failure is logged and
the PREVIOUS snapshot keeps being served rather than crashing the whole
service or serving empty/broken data.
"""

import logging
from dataclasses import dataclass, field
from datetime import datetime, timezone

import pandas as pd

from app.services.data_client import fetch_prompts_dataframe, fetch_reviews_dataframe

log = logging.getLogger("analytics_service.snapshot")


@dataclass
class Snapshot:
    prompts: pd.DataFrame = field(default_factory=pd.DataFrame)
    reviews: pd.DataFrame = field(default_factory=pd.DataFrame)
    last_refreshed_at: datetime | None = None
    last_refresh_error: str | None = None


class SnapshotStore:
    def __init__(self):
        self._snapshot = Snapshot()

    @property
    def current(self) -> Snapshot:
        return self._snapshot

    @property
    def has_data(self) -> bool:
        return self._snapshot.last_refreshed_at is not None

    async def refresh(self) -> None:
        try:
            prompts_df = await fetch_prompts_dataframe()
            reviews_df = await fetch_reviews_dataframe()

            self._snapshot = Snapshot(
                prompts=prompts_df,
                reviews=reviews_df,
                last_refreshed_at=datetime.now(timezone.utc),
                last_refresh_error=None,
            )
            log.info(
                "Snapshot refreshed: %d prompts, %d reviews",
                len(prompts_df),
                len(reviews_df),
            )

        except Exception as exc:  # noqa: BLE001 - genuinely want to catch anything here
            log.error("Snapshot refresh failed, keeping previous snapshot: %s", exc)
            # Keep the old snapshot's data, but record the error so callers
            # (e.g. the overview endpoint) can surface it if useful.
            self._snapshot.last_refresh_error = str(exc)


snapshot_store = SnapshotStore()
