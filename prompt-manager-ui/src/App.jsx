import { useEffect, useState } from "react";

import CreatePrompt from "./components/CreatePrompt";
import PromptList from "./components/PromptList";
import ReviewForm from "./components/ReviewForm";
import ReviewList from "./components/ReviewList";
import ReviewSummary from "./components/ReviewSummary";
import Toast from "./components/Toast";

function App() {

    const [promptRefresh, setPromptRefresh] = useState(false);
    const [reviewRefresh, setReviewRefresh] = useState(false);
    const [selectedPrompt, setSelectedPrompt] = useState(null);
    const [toast, setToast] = useState(null);

    useEffect(() => {

        if (!toast) return;

        const timer = setTimeout(() => setToast(null), 3200);
        return () => clearTimeout(timer);

    }, [toast]);

    function notify(message, type = "success") {
        setToast({ message, type });
    }

    function handlePromptCreated() {
        setPromptRefresh(!promptRefresh);
        notify("Prompt created.");
    }

    function handleReviewCreated() {
        setReviewRefresh(!reviewRefresh);
        notify("Review submitted.");
    }

    return (

        <div className="app-shell">

            <header className="app-header">

                <div className="brand">

                    <div className="brand-mark">P</div>

                    <div>
                        <span className="eyebrow">Prompt review workspace</span>
                        <h1>Prompt Manager</h1>
                    </div>

                </div>

                <p className="tagline">
                    Draft prompts, catalogue them by target model, and collect
                    structured peer feedback before anything ships.
                </p>

            </header>

            <div className="workspace">

                <aside>
                    <CreatePrompt
                        onPromptCreated={handlePromptCreated}
                        onError={(msg) => notify(msg, "error")}
                    />
                </aside>

                <div className="stack">

                    <PromptList
                        refresh={promptRefresh}
                        selectedPrompt={selectedPrompt}
                        onSelectPrompt={setSelectedPrompt}
                    />

                    <div className="review-grid">

                        <ReviewForm
                            selectedPrompt={selectedPrompt}
                            onReviewCreated={handleReviewCreated}
                            onError={(msg) => notify(msg, "error")}
                        />

                        <ReviewSummary
                            selectedPrompt={selectedPrompt}
                        />

                    </div>

                    <ReviewList
                        refresh={reviewRefresh}
                    />

                </div>

            </div>

            <Toast message={toast?.message} type={toast?.type} />

        </div>

    );

}

export default App;
