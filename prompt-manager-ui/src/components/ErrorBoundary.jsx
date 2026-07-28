import { Component } from "react";

// Class components are still the only way to catch render errors in React.
// Without this, any uncaught error anywhere in the tree (like the hooks-order
// bug that used to live in App.jsx) unmounts everything and leaves a blank
// page with no clue why.
class ErrorBoundary extends Component {

    constructor(props) {
        super(props);
        this.state = { error: null };
    }

    static getDerivedStateFromError(error) {
        return { error };
    }

    componentDidCatch(error, info) {
        console.error("Unhandled UI error:", error, info);
    }

    handleReset = () => {
        this.setState({ error: null });
    };

    render() {
        if (this.state.error) {
            return (
                <div className="app-shell auth-shell">
                    <div className="auth-card panel">
                        <h1 style={{ marginBottom: 12 }}>Something went wrong</h1>
                        <p className="panel-hint" style={{ marginBottom: 20 }}>
                            {this.state.error.message || "The app hit an unexpected error."}
                        </p>
                        <button
                            type="button"
                            className="btn-primary"
                            onClick={() => window.location.reload()}
                        >
                            Reload
                        </button>
                    </div>
                </div>
            );
        }

        return this.props.children;
    }
}

export default ErrorBoundary;
