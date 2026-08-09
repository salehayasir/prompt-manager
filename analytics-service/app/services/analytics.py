"""
The actual data-science content of Week 3: aggregation, grouping, and being
explicit about when a number is/isn't trustworthy.

Every function here takes the current Snapshot and does its work with
pandas operations (groupby, resample, merge) rather than manual Python
loops - that's the whole point of using pandas instead of Java.
"""

import pandas as pd

from app.services.snapshot import Snapshot

MIN_REVIEWS_FOR_LEADERBOARD = 2


def compute_overview(snapshot: Snapshot) -> dict:
    prompts = snapshot.prompts
    reviews = snapshot.reviews

    total_prompts = len(prompts)
    total_reviews = len(reviews)

    average_score = (
        round(reviews["score"].mean(), 2)
        if total_reviews > 0 and "score" in reviews.columns
        else None
    )

    prompts_with_attachment = (
        int(prompts["attachmentUrl"].notna().sum())
        if "attachmentUrl" in prompts.columns
        else 0
    )

    most_used_tag = _most_used_tag(prompts)

    return {
        "totalPrompts": total_prompts,
        "totalReviews": total_reviews,
        "averageScore": average_score,
        "promptsWithAttachment": prompts_with_attachment,
        "mostUsedTag": most_used_tag,
        "lastRefreshedAt": (
            snapshot.last_refreshed_at.isoformat()
            if snapshot.last_refreshed_at
            else None
        ),
        "lastRefreshError": snapshot.last_refresh_error,
    }


def _most_used_tag(prompts: pd.DataFrame) -> str | None:
    """
    tags is stored as a comma-separated string (e.g. "java,spring") -
    split it out into individual tags before counting.
    """
    if "tags" not in prompts.columns or prompts.empty:
        return None

    exploded = (
        prompts["tags"]
        .fillna("")
        .str.split(",")
        .explode()
        .str.strip()
    )
    exploded = exploded[exploded != ""]

    if exploded.empty:
        return None

    return exploded.value_counts().idxmax()


def compute_trends(snapshot: Snapshot, interval: str, days: int) -> dict:
    freq = "D" if interval == "day" else "W"

    cutoff = pd.Timestamp.now(tz="UTC") - pd.Timedelta(days=days)

    prompts = snapshot.prompts
    reviews = snapshot.reviews

    prompt_counts = _bucket_counts(prompts, "createdAt", cutoff, freq)
    review_counts = _bucket_counts(reviews, "reviewedAt", cutoff, freq)

    return {
        "interval": interval,
        "days": days,
        "promptsCreated": prompt_counts,
        "reviewsSubmitted": review_counts,
    }


def _bucket_counts(
    df: pd.DataFrame, date_col: str, cutoff: pd.Timestamp, freq: str
) -> list[dict]:
    if date_col not in df.columns or df.empty:
        return []

    series = df[date_col].dropna()

    # Normalize to UTC-aware timestamps so comparison against `cutoff` (which
    # is UTC-aware) doesn't raise on naive datetimes from the JSON payload.
    if series.dt.tz is None:
        series = series.dt.tz_localize("UTC")
    else:
        series = series.dt.tz_convert("UTC")

    series = series[series >= cutoff]

    if series.empty:
        return []

    bucketed = series.dt.to_period(freq).value_counts().sort_index()

    return [
        {"period": str(period), "count": int(count)}
        for period, count in bucketed.items()
    ]


def compute_tag_performance(snapshot: Snapshot) -> list[dict]:
    prompts = snapshot.prompts
    reviews = snapshot.reviews

    if prompts.empty or "tags" not in prompts.columns:
        return []

    # One row per (promptId, individual tag)
    exploded = prompts[["id", "tags"]].copy()
    exploded["tags"] = exploded["tags"].fillna("")
    exploded = exploded.assign(tag=exploded["tags"].str.split(",")).explode("tag")
    exploded["tag"] = exploded["tag"].str.strip()
    exploded = exploded[exploded["tag"] != ""]

    if exploded.empty:
        return []

    if reviews.empty or "promptId" not in reviews.columns:
        merged = exploded.assign(score=pd.NA)
    else:
        merged = exploded.merge(
            reviews[["promptId", "score"]],
            left_on="id",
            right_on="promptId",
            how="left",
        )

    grouped = (
        merged.groupby("tag")
        .agg(
            promptCount=("id", "nunique"),
            averageScore=("score", "mean"),
        )
        .reset_index()
    )

    grouped["averageScore"] = grouped["averageScore"].round(2)
    grouped = grouped.sort_values(
        "averageScore", ascending=False, na_position="last"
    )

    return grouped.to_dict(orient="records")


def compute_leaderboard(snapshot: Snapshot) -> dict:
    prompts = snapshot.prompts
    reviews = snapshot.reviews

    top_reviewers: list[dict] = []
    if not reviews.empty and "reviewerName" in reviews.columns:
        reviewer_counts = (
            reviews.groupby("reviewerName")
            .size()
            .sort_values(ascending=False)
            .head(5)
        )
        top_reviewers = [
            {"reviewerName": name, "reviewCount": int(count)}
            for name, count in reviewer_counts.items()
        ]

    top_prompts: list[dict] = []
    bottom_prompts: list[dict] = []

    if not reviews.empty and "promptId" in reviews.columns and not prompts.empty:
        stats = (
            reviews.groupby("promptId")
            .agg(reviewCount=("score", "size"), averageScore=("score", "mean"))
            .reset_index()
        )
        eligible = stats[stats["reviewCount"] >= MIN_REVIEWS_FOR_LEADERBOARD].copy()
        eligible["averageScore"] = eligible["averageScore"].round(2)

        if not eligible.empty:
            eligible = eligible.merge(
                prompts[["id", "name"]], left_on="promptId", right_on="id", how="left"
            )

            ranked = eligible.sort_values("averageScore", ascending=False)

            top_prompts = ranked.head(5)[
                ["promptId", "name", "averageScore", "reviewCount"]
            ].to_dict(orient="records")

            bottom_prompts = (
                ranked.tail(5)
                .sort_values("averageScore", ascending=True)[
                    ["promptId", "name", "averageScore", "reviewCount"]
                ]
                .to_dict(orient="records")
            )

    return {
        "minimumReviewsForRanking": MIN_REVIEWS_FOR_LEADERBOARD,
        "topReviewers": top_reviewers,
        "topPrompts": top_prompts,
        "bottomPrompts": bottom_prompts,
    }


def compute_correlation(snapshot: Snapshot) -> dict:
    """
    Pearson correlation between prompt content length and that prompt's
    AVERAGE review score. Deliberately reports sample size and a plain
    caveat alongside the number - the point of this endpoint is knowing
    when NOT to trust a correlation, not just computing one.
    """
    prompts = snapshot.prompts
    reviews = snapshot.reviews

    if (
        prompts.empty
        or reviews.empty
        or "promptId" not in reviews.columns
        or "content_length" not in prompts.columns
    ):
        return _empty_correlation_result()

    avg_scores = reviews.groupby("promptId")["score"].mean().reset_index()
    avg_scores.columns = ["id", "averageScore"]

    merged = prompts[["id", "content_length"]].merge(avg_scores, on="id", how="inner")
    merged = merged.dropna(subset=["content_length", "averageScore"])

    sample_size = len(merged)

    if sample_size < 2:
        return _empty_correlation_result(sample_size)

    correlation = merged["content_length"].corr(merged["averageScore"])

    return {
        "correlationCoefficient": (
            round(correlation, 4) if pd.notna(correlation) else None
        ),
        "sampleSize": sample_size,
        "caveat": _correlation_caveat(sample_size),
    }


def _empty_correlation_result(sample_size: int = 0) -> dict:
    return {
        "correlationCoefficient": None,
        "sampleSize": sample_size,
        "caveat": _correlation_caveat(sample_size),
    }


def _correlation_caveat(sample_size: int) -> str:
    base = (
        "Correlation does not imply causation - a relationship here does not "
        "mean content length causes a score change, or vice versa."
    )
    if sample_size < 30:
        base += (
            f" The sample size ({sample_size}) is small; treat this number as "
            "noise-prone and avoid drawing conclusions from it until more "
            "prompts have enough reviews."
        )
    return base
