function Toast({ message, type = "success" }) {

    if (!message) return null;

    return (
        <div className={`toast ${type === "error" ? "toast-error" : ""}`}>
            <span className="toast-dot" />
            {message}
        </div>
    );

}

export default Toast;
