import { useState } from "react";
import { reviewApi } from "../api/axios";

function ReviewForm({ selectedPrompt, onReviewCreated, onError }) {

    const [review, setReview] = useState({
        reviewerName: "",
        score: 1,
        feedback: ""
    });

    if (!selectedPrompt) {
        return (
            <div className="panel">
                <div className="panel-header">
                    <h2>Submit review</h2>
                </div>
                <div className="empty-state">
                    Select a prompt from the list above to review it.
                </div>
            </div>
        );
    }

    function handleChange(event) {

        setReview({
            ...review,
            [event.target.name]: event.target.value
        });

    }

    function handleSubmit(event) {

        event.preventDefault();

        reviewApi.post("/reviews", {

            promptId: selectedPrompt.id,
            reviewerName: review.reviewerName,
            score: Number(review.score),
            feedback: review.feedback

        })
        .then(() => {

            onReviewCreated();

            setReview({
                reviewerName: "",
                score: 1,
                feedback: ""
            });

        })
        .catch((error) => {

            console.error(error);
            onError?.("Couldn't submit the review. Check the service and try again.");

        });

    }

    return (

        <div className="panel">

            <div className="panel-header">
                <h2>Submit review</h2>
            </div>

            <p className="reviewing-target">
                Reviewing <strong>{selectedPrompt.name}</strong>
            </p>

            <form onSubmit={handleSubmit}>

                <div className="field">
                    <label htmlFor="reviewerName">Reviewer name</label>
                    <input
                        id="reviewerName"
                        name="reviewerName"
                        placeholder="Your name"
                        value={review.reviewerName}
                        onChange={handleChange}
                        required
                    />
                </div>

                <div className="field">
                    <label htmlFor="score">Score (1–5)</label>
                    <div className="score-input">
                        <input
                            id="score"
                            type="number"
                            name="score"
                            min="1"
                            max="5"
                            value={review.score}
                            onChange={handleChange}
                        />
                        <div className="score-meter" aria-hidden="true">
                            {[1, 2, 3, 4, 5].map((segment) => (
                                <span
                                    key={segment}
                                    className={segment <= Number(review.score) ? "filled" : ""}
                                />
                            ))}
                        </div>
                    </div>
                </div>

                <div className="field">
                    <label htmlFor="feedback">Feedback</label>
                    <textarea
                        id="feedback"
                        name="feedback"
                        placeholder="What worked, what didn't, what to change..."
                        value={review.feedback}
                        onChange={handleChange}
                    />
                </div>

                <button type="submit" className="btn-primary">
                    Submit review
                </button>

            </form>

        </div>

    );

}

export default ReviewForm;
