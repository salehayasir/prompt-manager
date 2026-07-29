import { useEffect, useState } from "react";

import CreatePrompt from "./components/CreatePrompt";
import PromptList from "./components/PromptList";
import ReviewForm from "./components/ReviewForm";
import ReviewList from "./components/ReviewList";
import ReviewSummary from "./components/ReviewSummary";
import Toast from "./components/Toast";
import Login from "./components/Login";
import { clearToken, getToken, onLogout } from "./api/authToken";

function App() {

    // All hooks live at the top level, unconditionally, on every render.
    // (Previously an early "if not authenticated, return <Login />" sat
    // above these, which meant React saw a different number of hooks
    // depending on auth state — a Rules-of-Hooks violation that crashed
    // the whole tree with a blank screen the moment login succeeded.)
    const [isAuthenticated, setIsAuthenticated] = useState(!!getToken());
    const [promptRefresh, setPromptRefresh] = useState(false);
    const [reviewRefresh, setReviewRefresh] = useState(false);
    const [selectedPrompt, setSelectedPrompt] = useState(null);
    const [toast, setToast] = useState(null);

    useEffect(() => {

        if (!toast) return;

        const timer = setTimeout(() => setToast(null), 3200);
        return () => clearTimeout(timer);

    }, [toast]);

    // If any API call gets a 401 (expired/invalid token), authToken.js
    // dispatches a logout event. Listen for it so the UI actually drops
    // back to the login screen instead of silently sitting there broken.
    useEffect(() => {
        return onLogout(() => setIsAuthenticated(false));
    }, []);

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

    function handleLogout() {
        clearToken({ silent: true });
        setIsAuthenticated(false);
        setSelectedPrompt(null);
    }

    if (!isAuthenticated) {
        return <Login onLogin={() => setIsAuthenticated(true)} />;
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

                <div className="header-actions">

                    <p className="tagline">
                        Draft prompts, catalogue them by target model, and collect
                        structured peer feedback before anything ships.
                    </p>

                    <button
                        type="button"
                        className="btn-secondary btn-small logout-btn"
                        onClick={handleLogout}
                    >
                        Log out
                    </button>

                </div>

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
