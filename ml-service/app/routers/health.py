from fastapi import APIRouter

router = APIRouter(tags=["health"])


@router.get("/health")
def health_check():
    """No auth required - same rule as the other three services."""
    return {"status": "UP"}
