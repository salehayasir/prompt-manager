let token = null;

// Simple pub-sub so any part of the app (App.jsx) can react when the token
// gets cleared - e.g. axios.js clearing it after a 401 should immediately
// drop the UI back to the login screen, without every caller needing to
// know about React state.
const logoutListeners = new Set();

export function setToken(newToken) {
    token = newToken;
}

export function getToken() {
    return token;
}

// options.silent = true skips notifying listeners - used when the user
// clicks "Log out" themselves, since the caller (App.jsx) already updates
// its own isAuthenticated state directly and doesn't need the event too.
export function clearToken(options = {}) {
    token = null;

    if (!options.silent) {
        logoutListeners.forEach((listener) => listener());
    }
}

// Subscribe to logout events (e.g. triggered by a 401 response). Returns an
// unsubscribe function, meant to be returned directly from a useEffect.
export function onLogout(listener) {
    logoutListeners.add(listener);
    return () => logoutListeners.delete(listener);
}
