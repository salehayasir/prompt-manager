import { useState } from "react";
import { authApi } from "../api/axios";
import { setToken } from "../api/authToken";

function Login({ onLogin }) {

    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleSubmit(e) {
        e.preventDefault();
        setError(null);
        setIsSubmitting(true);

        try {
            const response = await authApi.post("/login", { username, password });
            setToken(response.data.token);
            onLogin();
        } catch (err) {
            setError("Invalid username or password.");
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <div className="auth-shell">
            <div className="panel auth-card">

                <div className="brand auth-brand">

                    <div className="brand-mark">P</div>

                    <div>
                        <span className="eyebrow">Prompt review workspace</span>
                        <h1>Prompt Manager</h1>
                    </div>

                </div>

                <p className="auth-hint">
                    Sign in to manage prompts and reviews.
                </p>

                {error && <div className="form-error">{error}</div>}

                <form onSubmit={handleSubmit}>

                    <div className="field">
                        <label htmlFor="login-username">Username</label>
                        <input
                            id="login-username"
                            type="text"
                            autoComplete="username"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            required
                        />
                    </div>

                    <div className="field">
                        <label htmlFor="login-password">Password</label>
                        <input
                            id="login-password"
                            type="password"
                            autoComplete="current-password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            required
                        />
                    </div>

                    <button type="submit" className="btn-primary" disabled={isSubmitting}>
                        {isSubmitting ? "Signing in…" : "Sign in"}
                    </button>

                </form>

            </div>
        </div>
    );
}

export default Login;
