"""
Small, dependency-light vector math helpers for semantic search.
Kept separate from the store so the "what is a nearest neighbor" logic is
easy to find, read, and unit test on its own.
"""

import numpy as np


def cosine_similarity(a: list[float], b: list[float]) -> float:
    va, vb = np.array(a, dtype=float), np.array(b, dtype=float)
    denom = np.linalg.norm(va) * np.linalg.norm(vb)
    if denom == 0:
        return 0.0
    return float(np.dot(va, vb) / denom)


def top_k_by_similarity(
    query_vector: list[float],
    candidates: dict[str, list[float]],
    top_k: int,
    exclude_id: str | None = None,
) -> list[tuple[str, float]]:
    """
    candidates: {id: vector}. Returns [(id, score), ...] sorted by score
    descending, highest similarity first, capped at top_k.
    """
    scored = [
        (item_id, cosine_similarity(query_vector, vector))
        for item_id, vector in candidates.items()
        if item_id != exclude_id
    ]
    scored.sort(key=lambda pair: pair[1], reverse=True)
    return scored[:top_k]
