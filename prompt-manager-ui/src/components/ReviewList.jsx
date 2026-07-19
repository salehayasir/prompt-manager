import { useEffect, useState } from "react";
import { reviewApi } from "../api/axios";

function ReviewList({ refresh }) {

    const [reviews, setReviews] = useState([]);

    useEffect(() => {

        reviewApi.get("/reviews")
            .then((response) => {
                setReviews(response.data);
            })
            .catch(console.error);

    }, [refresh]);

    return (

        <div className="panel">

            <div className="panel-header">
                <h2>All reviews</h2>
                <span className="panel-count">{reviews.length} total</span>
            </div>

            {reviews.length === 0 ? (

                <div className="empty-state">
                    No reviews yet.
                </div>

            ) : (

                <div className="review-list">

                    {reviews.map((review) => (

                        <div key={review.id} className="review-item">

                            <div className="review-item-head">
                                <span className="review-item-title">
                                    {review.promptSnapshot.name}
                                </span>
                                <div className="score-meter" style={{ maxWidth: 90 }} aria-hidden="true">
                                    {[1, 2, 3, 4, 5].map((segment) => (
                                        <span
                                            key={segment}
                                            className={segment <= review.score ? "filled" : ""}
                                        />
                                    ))}
                                </div>
                            </div>

                            <div className="review-meta">
                                Reviewed by {review.reviewerName} · Score {review.score}/5
                            </div>

                            <p className="review-feedback">
                                {review.feedback || "No feedback given."}
                            </p>

                        </div>

                    ))}

                </div>
            )}

        </div>

    );

}

export default ReviewList;
