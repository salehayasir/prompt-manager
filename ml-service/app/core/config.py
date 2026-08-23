"""
Centralized configuration for ml-service.

Every value comes from an environment variable - nothing sensitive
(JWT_SECRET, service-account credentials, the Hugging Face token) is ever
hardcoded, matching the rule Week 2 set for prompt-service and review-service
and Week 3 kept for analytics-service.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    ml_service_port: int = 8003

    jwt_secret: str
    jwt_algorithm: str = "HS512"  # matches prompt-service's HS512 signing

    prompt_service_url: str
    review_service_url: str

    ml_service_username: str
    ml_service_password: str

    huggingface_api_token: str
    hf_embedding_model: str = "sentence-transformers/all-MiniLM-L6-v2"
    hf_sentiment_model: str = "distilbert-base-uncased-finetuned-sst-2-english"

    ml_refresh_interval_sec: int = 120

    # Minimum model confidence required before a sentiment/score disagreement
    # is flagged - see README for why this value was chosen.
    sentiment_confidence_threshold: float = 0.75


# Loaded once at import time; FastAPI dependency injection reuses this
# single instance everywhere via get_settings() below.
settings = Settings()


def get_settings() -> Settings:
    return settings
