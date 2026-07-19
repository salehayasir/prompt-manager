import { useEffect, useState } from "react";
import { promptApi } from "../api/axios";

function PromptList({ refresh, selectedPrompt, onSelectPrompt }) {

    const [prompts, setPrompts] = useState([]);

    useEffect(() => {

        promptApi.get("")
            .then((response) => {
                setPrompts(response.data);
            })
            .catch(console.error);

    }, [refresh]);

    return (
        <div className="panel">

            <div className="panel-header">
                <h2>Prompts</h2>
                <span className="panel-count">{prompts.length} total</span>
            </div>

            {prompts.length === 0 ? (

                <div className="empty-state">
                    No prompts yet — create one to get started.
                </div>

            ) : (

                <div className="prompt-grid">

                    {prompts.map((prompt) => {

                        const isSelected = selectedPrompt?.id === prompt.id;
                        const tags = prompt.tags
                            ? prompt.tags.split(",").map((tag) => tag.trim()).filter(Boolean)
                            : [];

                        return (

                            <div
                                key={prompt.id}
                                className={`prompt-card ${isSelected ? "is-selected" : ""}`}
                            >

                                <div className="prompt-card-title">{prompt.name}</div>

                                <div className="prompt-card-desc">
                                    {prompt.description || "No description provided."}
                                </div>

                                <div className="tag-row">

                                    {prompt.modelTarget && (
                                        <span className="chip chip-model">{prompt.modelTarget}</span>
                                    )}

                                    {tags.map((tag) => (
                                        <span key={tag} className="chip">{tag}</span>
                                    ))}

                                </div>

                                <button
                                    className="btn-secondary btn-small"
                                    onClick={() => onSelectPrompt(prompt)}
                                >
                                    {isSelected ? "Selected" : "Review this prompt"}
                                </button>

                            </div>

                        );

                    })}

                </div>

            )}

        </div>
    );
}

export default PromptList;
