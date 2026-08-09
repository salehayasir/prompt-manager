"""
JWT verification for analytics-service's OWN incoming requests.

This is deliberately separate from app/services/auth_client.py, which
handles analytics-service logging ITSELF in as a client of prompt-service.
Those are two different problems that happen to both involve tokens:

  - security.py   -> "is the caller of MY endpoints allowed in?"
  - auth_client.py -> "how do I get a token so I can call prompt-service
                        and review-service?"

Verification here is done by hand with PyJWT (decode + signature + expiry
check) rather than just checking a header is present, matching the Java
JwtAuthenticationFilter's behavior in prompt-service/review-service.
"""

from fastapi import Header, HTTPException, status
import jwt

from app.core.config import get_settings


def _error_body(message: str) -> dict:
    """
    Same shape prompt-service/review-service already return from their
    GlobalExceptionHandler / JwtAuthenticationEntryPoint, so a 401 from any
    of the three services looks identical to a client.
    """
    from datetime import datetime, timezone

    return {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "status": status.HTTP_401_UNAUTHORIZED,
        "error": "Unauthorized",
        "message": message,
    }


def verify_token(authorization: str | None = Header(default=None)) -> str:
    """
    FastAPI dependency: attach with `Depends(verify_token)` on every route
    that needs auth. Returns the token's subject (username) on success,
    raises 401 on anything else.
    """
    settings = get_settings()

    if authorization is None or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=_error_body("Missing or malformed Authorization header"),
        )

    token = authorization.removeprefix("Bearer ").strip()

    try:
        payload = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=[settings.jwt_algorithm],
        )
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=_error_body("Token has expired"),
        )
    except jwt.InvalidTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=_error_body("Invalid token"),
        )

    subject = payload.get("sub")
    if subject is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=_error_body("Token missing subject claim"),
        )

    return subject
