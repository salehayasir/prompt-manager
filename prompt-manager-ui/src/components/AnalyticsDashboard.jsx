import { useEffect, useState } from "react";
import {
    Bar,
    BarChart,
    CartesianGrid,
    Legend,
    Line,
    LineChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from "recharts";

import { analyticsApi } from "../api/axios";

// Merges the two separate day/week-bucketed series from /analytics/trends
// into one array keyed by period, so recharts can draw both lines off a
// single dataset (a period with prompts but no reviews that day, or vice
// versa, still gets a row - just with the other value at 0).
function mergeTrendSeries(promptsCreated, reviewsSubmitted) {
    const byPeriod = new Map();

    for (const { period, count } of promptsCreated) {
        byPeriod.set(period, { period, prompts: count, reviews: 0 });
    }

    for (const { period, count } of reviewsSubmitted) {
        const existing = byPeriod.get(period);
        if (existing) {
            existing.reviews = count;
        } else {
            byPeriod.set(period, { period, prompts: 0, reviews: count });
        }
    }

    return Array.from(byPeriod.values()).sort((a, b) =>
        a.period.localeCompare(b.period)
    );
}

function AnalyticsDashboard() {

    const [overview, setOverview] = useState(null);
    const [trends, setTrends] = useState(null);
    const [tags, setTags] = useState(null);
    const [leaderboard, setLeaderboard] = useState(null);
    const [correlation, setCorrelation] = useState(null);
    const [error, setError] = useState(null);
    const [loading, setLoading] = useState(true);

    function loadAll() {

        setLoading(true);
        setError(null);

        Promise.all([
            analyticsApi.get("/overview"),
            analyticsApi.get("/trends", { params: { interval: "day", days: 30 } }),
            analyticsApi.get("/tags"),
            analyticsApi.get("/leaderboard"),
            analyticsApi.get("/correlation"),
        ])
            .then(([overviewRes, trendsRes, tagsRes, leaderboardRes, correlationRes]) => {
                setOverview(overviewRes.data);
                setTrends(trendsRes.data);
                setTags(tagsRes.data);
                setLeaderboard(leaderboardRes.data);
                setCorrelation(correlationRes.data);
            })
            .catch((err) => {
                console.error("Analytics load error:", err);
                setError(
                    "Couldn't load analytics. Make sure analytics-service is running on port 8002."
                );
            })
            .finally(() => setLoading(false));

    }

    useEffect(() => {
        loadAll();
    }, []);

    if (loading) {
        return (
            <div className="panel">
                <div className="empty-state">Loading analytics...</div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="panel">
                <div className="empty-state error-state">{error}</div>
                <button type="button" className="btn-secondary btn-small" onClick={loadAll}>
                    Retry
                </button>
            </div>
        );
    }

    const trendData = mergeTrendSeries(
        trends.promptsCreated,
        trends.reviewsSubmitted
    );

    const tagChartData = tags
        .filter((t) => t.averageScore !== null)
        .map((t) => ({ ...t, averageScore: Number(t.averageScore) }));

    return (

        <div className="analytics-dashboard">

            <div className="panel-header analytics-refresh-row">
                <h2>Analytics</h2>
                <button type="button" className="btn-secondary btn-small" onClick={loadAll}>
                    Refresh
                </button>
            </div>

            {overview.lastRefreshError && (
                <div className="empty-state error-state" style={{ marginBottom: 16 }}>
                    Last background refresh in analytics-service failed:{" "}
                    {overview.lastRefreshError} (showing the last successful snapshot)
                </div>
            )}

            {/* --- Overview --- */}
            <div className="panel">

                <div className="panel-header">
                    <h2>Overview</h2>
                    {overview.lastRefreshedAt && (
                        <span className="analytics-timestamp">
                            Snapshot refreshed{" "}
                            {new Date(overview.lastRefreshedAt).toLocaleString()}
                        </span>
                    )}
                </div>

                <div className="summary-stats analytics-stat-grid">

                    <div className="stat-block">
                        <div className="stat-label">Total prompts</div>
                        <div className="stat-value">{overview.totalPrompts}</div>
                    </div>

                    <div className="stat-block">
                        <div className="stat-label">Total reviews</div>
                        <div className="stat-value">{overview.totalReviews}</div>
                    </div>

                    <div className="stat-block">
                        <div className="stat-label">Average score</div>
                        <div className="stat-value">
                            {overview.averageScore ?? "—"}
                        </div>
                    </div>

                    <div className="stat-block">
                        <div className="stat-label">With attachment</div>
                        <div className="stat-value">{overview.promptsWithAttachment}</div>
                    </div>

                    <div className="stat-block">
                        <div className="stat-label">Most used tag</div>
                        <div className="stat-value stat-value-text">
                            {overview.mostUsedTag ?? "—"}
                        </div>
                    </div>

                </div>

            </div>

            {/* --- Trends --- */}
            <div className="panel">

                <div className="panel-header">
                    <h2>Trends (last {trends.days} days, by {trends.interval})</h2>
                </div>

                {trendData.length === 0 ? (
                    <div className="empty-state">No activity in this window yet.</div>
                ) : (
                    <ResponsiveContainer width="100%" height={260}>
                        <LineChart data={trendData}>
                            <CartesianGrid stroke="var(--line-soft)" />
                            <XAxis
                                dataKey="period"
                                tick={{ fontSize: 12, fill: "var(--ink-soft)" }}
                            />
                            <YAxis
                                allowDecimals={false}
                                tick={{ fontSize: 12, fill: "var(--ink-soft)" }}
                            />
                            <Tooltip />
                            <Legend />
                            <Line
                                type="monotone"
                                dataKey="prompts"
                                name="Prompts created"
                                stroke="var(--accent)"
                                strokeWidth={2}
                                dot={{ r: 3 }}
                            />
                            <Line
                                type="monotone"
                                dataKey="reviews"
                                name="Reviews submitted"
                                stroke="var(--gold)"
                                strokeWidth={2}
                                dot={{ r: 3 }}
                            />
                        </LineChart>
                    </ResponsiveContainer>
                )}

            </div>

            {/* --- Tag performance --- */}
            <div className="panel">

                <div className="panel-header">
                    <h2>Tag performance</h2>
                </div>

                {tagChartData.length === 0 ? (
                    <div className="empty-state">
                        No tags have any reviewed prompts yet.
                    </div>
                ) : (
                    <ResponsiveContainer width="100%" height={Math.max(220, tagChartData.length * 34)}>
                        <BarChart data={tagChartData} layout="vertical" margin={{ left: 24 }}>
                            <CartesianGrid stroke="var(--line-soft)" horizontal={false} />
                            <XAxis
                                type="number"
                                domain={[0, 5]}
                                tick={{ fontSize: 12, fill: "var(--ink-soft)" }}
                            />
                            <YAxis
                                type="category"
                                dataKey="tag"
                                width={100}
                                tick={{ fontSize: 12, fill: "var(--ink-soft)" }}
                            />
                            <Tooltip />
                            <Bar dataKey="averageScore" name="Average score" fill="var(--accent)" radius={[0, 4, 4, 0]} />
                        </BarChart>
                    </ResponsiveContainer>
                )}

                <p className="analytics-footnote">
                    Only tags whose prompts have at least one review are shown.
                    Check the review count behind a tag before trusting its score -
                    see the leaderboard below.
                </p>

            </div>

            {/* --- Leaderboard --- */}
            <div className="panel">

                <div className="panel-header">
                    <h2>Leaderboard</h2>
                    <span className="analytics-timestamp">
                        Minimum {leaderboard.minimumReviewsForRanking} reviews to rank
                    </span>
                </div>

                <div className="leaderboard-grid">

                    <div>
                        <h3 className="analytics-subheading">Top reviewers</h3>
                        {leaderboard.topReviewers.length === 0 ? (
                            <div className="empty-state">No reviews yet.</div>
                        ) : (
                            <ol className="leaderboard-list">
                                {leaderboard.topReviewers.map((r) => (
                                    <li key={r.reviewerName}>
                                        <span>{r.reviewerName}</span>
                                        <span className="leaderboard-count">{r.reviewCount}</span>
                                    </li>
                                ))}
                            </ol>
                        )}
                    </div>

                    <div>
                        <h3 className="analytics-subheading">Top prompts</h3>
                        {leaderboard.topPrompts.length === 0 ? (
                            <div className="empty-state">
                                No prompt has {leaderboard.minimumReviewsForRanking}+ reviews yet.
                            </div>
                        ) : (
                            <ol className="leaderboard-list">
                                {leaderboard.topPrompts.map((p) => (
                                    <li key={p.promptId}>
                                        <span>{p.name ?? p.promptId}</span>
                                        <span className="leaderboard-count">
                                            {p.averageScore} ({p.reviewCount})
                                        </span>
                                    </li>
                                ))}
                            </ol>
                        )}
                    </div>

                    <div>
                        <h3 className="analytics-subheading">Bottom prompts</h3>
                        {leaderboard.bottomPrompts.length === 0 ? (
                            <div className="empty-state">
                                No prompt has {leaderboard.minimumReviewsForRanking}+ reviews yet.
                            </div>
                        ) : (
                            <ol className="leaderboard-list">
                                {leaderboard.bottomPrompts.map((p) => (
                                    <li key={p.promptId}>
                                        <span>{p.name ?? p.promptId}</span>
                                        <span className="leaderboard-count">
                                            {p.averageScore} ({p.reviewCount})
                                        </span>
                                    </li>
                                ))}
                            </ol>
                        )}
                    </div>

                </div>

            </div>

            {/* --- Correlation --- */}
            <div className="panel">

                <div className="panel-header">
                    <h2>Content length vs. review score</h2>
                </div>

                <div className="summary-stats">

                    <div className="stat-block">
                        <div className="stat-label">Correlation coefficient</div>
                        <div className="stat-value">
                            {correlation.correlationCoefficient ?? "—"}
                        </div>
                    </div>

                    <div className="stat-block">
                        <div className="stat-label">Sample size</div>
                        <div className="stat-value">{correlation.sampleSize}</div>
                    </div>

                </div>

                <p className="analytics-footnote analytics-caveat">
                    {correlation.caveat}
                </p>

            </div>

        </div>

    );

}

export default AnalyticsDashboard;
