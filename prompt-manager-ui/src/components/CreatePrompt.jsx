import { useState } from "react";
import { promptApi } from "../api/axios";

function CreatePrompt({ onPromptCreated, onError }) {

    const [formData, setFormData] = useState({
        name: "",
        description: "",
        content: "",
        modelTarget: "",
        tags: ""
    });

    function handleChange(event) {

        setFormData({
            ...formData,
            [event.target.name]: event.target.value
        });

    }

    function handleSubmit(event) {

        event.preventDefault();

        promptApi.post("/prompts", formData)
            .then(() => {

                setFormData({
                    name: "",
                    description: "",
                    content: "",
                    modelTarget: "",
                    tags: ""
                });

                onPromptCreated();

            })
            .catch((error) => {
                console.error(error);
                onError?.("Couldn't create the prompt. Check the service and try again.");
            });

    }

    return (

        <div className="panel">

            <div className="panel-header">
                <h2>Create prompt</h2>
            </div>

            <p className="panel-hint">
                Add a prompt to the catalogue so it can be reviewed.
            </p>

            <form onSubmit={handleSubmit}>

                <div className="field">
                    <label htmlFor="name">Name</label>
                    <input
                        id="name"
                        name="name"
                        placeholder="e.g. Support ticket summarizer"
                        value={formData.name}
                        onChange={handleChange}
                        required
                    />
                </div>

                <div className="field">
                    <label htmlFor="description">Description</label>
                    <input
                        id="description"
                        name="description"
                        placeholder="What this prompt is for"
                        value={formData.description}
                        onChange={handleChange}
                    />
                </div>

                <div className="field">
                    <label htmlFor="content">Prompt content</label>
                    <textarea
                        id="content"
                        name="content"
                        placeholder="Write the full prompt text..."
                        value={formData.content}
                        onChange={handleChange}
                        required
                    />
                </div>

                <div className="field">
                    <label htmlFor="modelTarget">Model target</label>
                    <input
                        id="modelTarget"
                        name="modelTarget"
                        placeholder="e.g. gpt-4o, claude-sonnet"
                        value={formData.modelTarget}
                        onChange={handleChange}
                    />
                </div>

                <div className="field">
                    <label htmlFor="tags">Tags</label>
                    <input
                        id="tags"
                        name="tags"
                        placeholder="comma, separated, tags"
                        value={formData.tags}
                        onChange={handleChange}
                    />
                    <p className="hint">Used to filter and group prompts later.</p>
                </div>

                <button type="submit" className="btn-primary">
                    Create prompt
                </button>

            </form>

        </div>

    );

}

export default CreatePrompt;
