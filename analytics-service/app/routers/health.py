from fastapi import APIRouter

router = APIRouter(tags=["health"])


@router.get("/health")
def health_check():
    """No auth required - same rule as prompt-service and review-service."""
    return {"status": "UP"}
