import { useEffect, useState } from "react";
import { reviewApi } from "../api/axios";

function ReviewSummary({ selectedPrompt }) {

    const [summary, setSummary] = useState(null);

    useEffect(() => {

        if (!selectedPrompt) {
            setSummary(null);
            return;
        }

        reviewApi.get(`/reviews/prompt/${selectedPrompt.id}/summary`)
            .then((response) => {
                setSummary(response.data);
            })
            .catch((error) => {
                console.error("Summary error:", error);
            });

    }, [selectedPrompt]);

    if (!selectedPrompt) {

        return (
            <div className="panel">
                <div className="panel-header">
                    <h2>Review summary</h2>
                </div>
                <div className="empty-state">
                    Select a prompt to see its review summary.
                </div>
            </div>
        );

    }

    if (!summary) {

        return (
            <div className="panel">
                <div className="panel-header">
                    <h2>Review summary</h2>
                </div>
                <div className="empty-state">
                    Loading summary...
                </div>
            </div>
        );

    }

    const roundedScore = Math.round(summary.averageScore || 0);

    return (

        <div className="panel">

            <div className="panel-header">
                <h2>Review summary</h2>
            </div>

            <p className="reviewing-target">
                <strong>{selectedPrompt.name}</strong>
            </p>

            <div className="summary-stats">

                <div className="stat-block">
                    <div className="stat-label">Average score</div>
                    <div className="stat-value">{summary.averageScore}</div>
                </div>

                <div className="stat-block">
                    <div className="stat-label">Total reviews</div>
                    <div className="stat-value">{summary.totalReviews}</div>
                </div>

            </div>

            <div className="score-meter" aria-hidden="true" style={{ marginTop: 16 }}>
                {[1, 2, 3, 4, 5].map((segment) => (
                    <span
                        key={segment}
                        className={segment <= roundedScore ? "filled" : ""}
                    />
                ))}
            </div>

        </div>

    );

}

export default ReviewSummary;
