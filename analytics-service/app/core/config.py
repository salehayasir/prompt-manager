"""
Centralized configuration for analytics-service.

Every value here comes from an environment variable - nothing sensitive
(JWT_SECRET, service-account credentials) is ever hardcoded, matching the
rule Week 2 set for prompt-service and review-service.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    analytics_service_port: int = 8002

    jwt_secret: str
    jwt_algorithm: str = "HS512"  # matches prompt-service's HS512 signing

    prompt_service_url: str
    review_service_url: str

    analytics_service_username: str
    analytics_service_password: str

    analytics_refresh_interval_sec: int = 60


# Loaded once at import time; FastAPI dependency injection reuses this
# single instance everywhere via get_settings() below.
settings = Settings()


def get_settings() -> Settings:
    return settings
