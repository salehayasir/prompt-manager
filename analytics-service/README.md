# analytics-service

A Python/FastAPI service that reads data from `prompt-service` and
`review-service` over HTTP and turns it into trends, tag performance, a
leaderboard, and a correlation check. It has no database of its own -
everything is derived from calling the other two services, exactly the way
any other client would.

## Setup

```bash
cd analytics-service
python -m venv venv
source venv/bin/activate      # Windows: venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env          # then fill in real values
```

Required environment variables (see `.env.example`):

| Variable | Purpose |
|---|---|
| `ANALYTICS_SERVICE_PORT` | Port this service runs on (default 8002) |
| `JWT_SECRET` | Must match prompt-service/review-service exactly |
| `PROMPT_SERVICE_URL` | Base URL for prompt-service |
| `REVIEW_SERVICE_URL` | Base URL for review-service |
| `ANALYTICS_SERVICE_USERNAME` / `ANALYTICS_SERVICE_PASSWORD` | Credentials analytics-service logs in with |
| `ANALYTICS_REFRESH_INTERVAL_SEC` | How often the background job refreshes the snapshot |

## Running

```bash
uvicorn app.main:app --port 8002 --reload
```

prompt-service and review-service must both be running and reachable at the
URLs configured above, since analytics-service logs in and fetches data from
both at startup.

## Endpoints

- `GET /health` - no auth required
- `GET /analytics/overview` - totals, average score, most-used tag, last refresh time
- `GET /analytics/trends?interval=day|week&days=30` - prompts/reviews bucketed over time
- `GET /analytics/tags` - average score per tag
- `GET /analytics/leaderboard` - top reviewers, top/bottom prompts (min. 2 reviews to qualify)
- `GET /analytics/correlation` - content length vs. average score, with sample size and a caveat

All except `/health` require `Authorization: Bearer <token>` using the same
JWT scheme from Week 2 - log in via `POST /auth/login` on prompt-service to
get a token.

## Known simplification: shared login account

analytics-service authenticates to prompt-service/review-service using the
**same single login endpoint and account mechanism** built in Week 2 for a
human user, just with a second, dedicated username/password
(`ANALYTICS_SERVICE_USERNAME` / `ANALYTICS_SERVICE_PASSWORD`).

This is a known shortcut, not a recommended design. In a real system, a
background/machine caller would typically use a separate mechanism from
human login - e.g. a service-account or client-credentials OAuth flow, API
keys, or mutual TLS - rather than sharing the same login endpoint and token
shape as a person signing into a UI. Flagging this explicitly rather than
presenting it as the ideal approach.

## Data collection and resilience

- All data is fetched by paging through prompt-service's `/prompts` and
  review-service's `/reviews` (Week 2's `Pageable`-based endpoints) until
  every page is retrieved - never just the first page.
- If either service is unreachable when a scheduled refresh runs, the
  failure is logged and the **previous successful snapshot keeps being
  served** rather than crashing the service or returning empty/broken data.
  The `lastRefreshError` field on `/analytics/overview` surfaces this if it
  happens.
- If a call to prompt-service/review-service returns 401 (token expired),
  analytics-service re-authenticates once and retries automatically.
