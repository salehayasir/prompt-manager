import { useState } from "react";
import { authApi } from "../api/axios";
import { setToken } from "../api/authToken";

function Login({ onLogin }) {

    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState(null);

    async function handleSubmit(e) {
        e.preventDefault();
        setError(null);

        try {
            const response = await authApi.post("/login", { username, password });
            setToken(response.data.token);
            onLogin();
        } catch (err) {
            setError("Invalid username or password.");
        }
    }

    return (
        <div className="app-shell">
            <div className="workspace" style={{ maxWidth: 360, margin: "80px auto" }}>
                <h1>Sign in</h1>
                <form onSubmit={handleSubmit}>
                    <input
                        placeholder="Username"
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                    />
                    <input
                        type="password"
                        placeholder="Password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                    />
                    <button type="submit">Log in</button>
                </form>
                {error && <p style={{ color: "red" }}>{error}</p>}
            </div>
        </div>
    );
}

export default Login;