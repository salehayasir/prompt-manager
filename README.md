# Prompt Manager

A microservice-based web application for creating, organizing, and reviewing AI prompts. Built as a Software Engineering internship project.

**Live demo:** [rogue-muster-attentive.ngrok-free.dev](https://rogue-muster-attentive.ngrok-free.dev)
**Repository:** [github.com/salehayasir/prompt-manager](https://github.com/salehayasir/prompt-manager)

---

## Overview

Prompt Manager lets users create and catalog reusable AI prompts (name, description, content, tags, target model) and attach reviews/ratings to them. It's built as four independent microservices behind an Nginx gateway — two in Java/Spring Boot and two in Python/FastAPI — with a React single-page frontend.

## Architecture

![Architecture diagram](docs/architecture.png)

- **React SPA** is served as static files by Nginx and talks only to relative `/api/...` paths — no CORS, no hardcoded hosts. Includes the prompt/review workspace and an **Analytics** tab that visualizes analytics-service's endpoints (trends chart, tag-performance chart, leaderboard, correlation).
- **Nginx** is the single entry point. It serves the built frontend and reverse-proxies API traffic to whichever backend service owns it, rewriting `/api/prompts/*` → `/prompts/*`, `/api/reviews/*` → `/reviews/*`, `/api/analytics/*` → `/analytics/*`, and `/api/ml/*` → `/ml/*` so each backend service can keep clean, prefix-free route mappings.
- **prompt-service** (Spring Boot, port `8000`) owns prompt CRUD and persists to **PostgreSQL** via Spring Data JPA. It also issues and validates JWTs (`POST /auth/login`), stores reference-file attachments on **Cloudinary**, keeps single-prompt lookups in an in-memory cache, and returns paginated/sorted/filtered listings.
- **review-service** (Spring Boot, port `8001`) owns reviews, persists each one as a JSON file on disk, and calls back into `prompt-service` over REST (`RestClient`) to validate that a prompt exists before accepting a review for it. It validates the same JWT (shared secret) rather than issuing its own, runs a scheduled digest job that aggregates review stats in memory, and fires an async, non-blocking notification (written to `notifications.log`) whenever a review is created.
- **analytics-service** (Python/FastAPI, port `8002`) is a read-only, polyglot addition with no database of its own. It logs into `prompt-service`'s shared `/auth/login` with a dedicated service account, pages through both other services' paginated list endpoints, and loads the results into pandas DataFrames to compute trends, tag performance, a reviewer/prompt leaderboard, and a content-length/score correlation check. It verifies incoming JWTs itself (via PyJWT, against the same `JWT_SECRET`) — proving the Week 2 auth scheme isn't tied to Spring Security. A background job (APScheduler) refreshes the underlying data on an interval rather than on every request, and if either Java service is unreachable at refresh time, it logs the failure and keeps serving the last successful snapshot instead of crashing or returning empty data.
- **ml-service** (Python/FastAPI, port `8003`) is the first service to apply real pretrained ML models to prompt-manager's own data instead of just aggregating it. It logs into `prompt-service` the same way analytics-service does, then calls the **Hugging Face Inference API** for two features: semantic search (embed every prompt, rank by cosine similarity) and a review quality check (score each review's feedback text for sentiment and flag it when that sentiment disagrees — confidently — with its numeric score). A background job (APScheduler) does an *incremental* refresh: it only re-embeds a prompt or re-scores a review if its content actually changed since the last cycle, which is what keeps the service inside Hugging Face's free-tier rate limit. It also explicitly distinguishes a model "cold start" (retry after a short wait) from a genuine failure or a rate limit (back off until the next cycle) rather than treating every non-200 response the same way.
- **Cloudinary** is the external file-storage provider for prompt attachments — prompt-service is the only caller; the API key/secret never reach the frontend.
- **Hugging Face Inference API** is the external, free, hosted ML provider — ml-service is the only caller; the API token never reaches the frontend.
- **ngrok** tunnels the public demo URL to the Nginx gateway running locally, so the whole stack is reachable without deploying to a cloud host.

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 19, Vite, Axios, Recharts (analytics charts) |
| Gateway | Nginx (static hosting + reverse proxy) |
| Backend (Java) | Java 17, Spring Boot 4.1 (Web MVC, Data JPA, Validation, Security, Cache, Scheduling, Async, Actuator) |
| Backend (Python) | Python 3.12, FastAPI, Uvicorn, pandas (analytics-service), numpy (ml-service), httpx, PyJWT, APScheduler, python-dotenv |
| ML | Hugging Face Inference API — sentence-transformers/all-MiniLM-L6-v2 (embeddings), distilbert-base-uncased-finetuned-sst-2-english (sentiment) |
| Database | PostgreSQL (prompt-service) |
| Storage | Local JSON files + `notifications.log` (review-service), Cloudinary (prompt attachments) |
| Auth | JWT (`jjwt` on the Java side, `PyJWT` on the Python side), shared signing secret across all four services |
| Caching | Spring Cache abstraction, in-memory `ConcurrentMapCacheManager` |
| API Docs | springdoc-openapi / Swagger UI (Java), FastAPI's built-in `/docs` (Python) |
| Tunneling | ngrok |

## Project Structure

```
prompt-manager/
├── prompt-manager-ui/     # React + Vite frontend (workspace + analytics dashboard)
├── prompt-service/        # Spring Boot microservice — prompts (Postgres)
├── review-service/        # Spring Boot microservice — reviews (JSON files)
├── analytics-service/     # FastAPI microservice — read-only analytics over the other two
├── ml-service/            # FastAPI microservice — semantic search + review quality (Hugging Face)
├── nginx/
│   └── nginx.conf         # Reverse proxy + static file serving
└── docs/                  # Architecture diagram (architecture.dot/.svg/.png)
```

## API Reference

All endpoints below (except `/auth/login`) require a `Authorization: Bearer <token>` header. Get a token first:

```bash
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"<AUTH_USERNAME>","password":"<AUTH_PASSWORD>"}'
```
Response: `{"token": "...", "expiresInMs": 3600000}`. Both services validate this token using the same `JWT_SECRET` — `review-service` doesn't issue its own tokens, only checks them.

### prompt-service (`/prompts`, proxied at `/api/prompts`)

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/login` | Log in with `AUTH_USERNAME`/`AUTH_PASSWORD`, get a JWT |
| `POST` | `/prompts` | Create a prompt |
| `GET` | `/prompts` | Paginated prompt listing. Query params: `page` (default `0`), `size` (default `10`), `sortBy` (any `Prompt` field, default `createdAt`), `direction` (`asc`/`desc`, default `asc`), `tag` (optional, case-insensitive substring filter) |
| `GET` | `/prompts/{id}` | Get a single prompt (served from cache on repeat requests — see below) |
| `PUT` | `/prompts/{id}` | Update a prompt (refreshes the cache entry) |
| `DELETE` | `/prompts/{id}` | Delete a prompt (evicts the cache entry) |
| `GET` | `/prompts/{id}/exists` | Check whether a prompt exists |
| `POST` | `/prompts/{id}/attachment` | Upload a reference file (multipart `file` field) to Cloudinary and attach it to the prompt |
| `DELETE` | `/prompts/{id}/attachment` | Delete the prompt's attachment from Cloudinary and clear it from the prompt |

**Pagination response shape** (both list endpoints return this):
```json
{
  "content": [ /* page of items */ ],
  "totalElements": 13,
  "totalPages": 2,
  "currentPage": 0,
  "pageSize": 10
}
```

**Caching:** `GET /prompts/{id}` is backed by an in-memory Spring cache (`ConcurrentMapCacheManager`, no Redis required). The server logs `CACHE MISS` on the first lookup for a given id and `CACHE HIT` on subsequent ones with no database query in between; `PUT`/`DELETE`/attachment changes refresh or evict that entry so the cache never serves stale data. Example:
```bash
curl http://localhost:8000/prompts/{id} -H "Authorization: Bearer <token>"   # CACHE MISS, hits DB
curl http://localhost:8000/prompts/{id} -H "Authorization: Bearer <token>"   # CACHE HIT, no DB query
```

**Attachment upload example:**
```bash
curl -X POST http://localhost:8000/prompts/{id}/attachment \
  -H "Authorization: Bearer <token>" \
  -F "file=@screenshot.png"
```
Allowed types: PNG/JPEG/GIF/WebP images, PDF, plain text, `.doc`/`.docx`. Returns `400` for a missing/unsupported file, `404` if the prompt doesn't exist, `502` if Cloudinary rejects the request, `503` if Cloudinary can't be reached at all.

### review-service (`/reviews`, proxied at `/api/reviews`)

| Method | Path | Description |
|---|---|---|
| `POST` | `/reviews` | Create a review for a prompt. Returns as soon as the review is saved — a notification is fired asynchronously afterward and does not delay the response |
| `GET` | `/reviews` | Paginated review listing. Query params: `page`, `size`, `sortBy` (any `Review` field), `direction`, plus filters `promptId`, `reviewerName`, `minScore`, `maxScore` (all optional) |
| `GET` | `/reviews/{id}` | Get a single review by id (404 if it doesn't exist) |
| `GET` | `/reviews/prompt/{promptId}` | List all reviews for a specific prompt |
| `GET` | `/reviews/{promptId}/summary` | Aggregated review summary for a prompt |
| `GET` | `/reviews/digest/latest` | Latest scheduled digest: total review count, average score, and the id of the highest (average-scoring) prompt |

Same pagination response shape as prompt-service (`content` + `totalElements`/`totalPages`/`currentPage`/`pageSize`).

**Scheduled digest:** a `@Scheduled` job runs every `DIGEST_INTERVAL_MS` (also once immediately at startup), recomputing stats from all reviews and logging the result. `GET /reviews/digest/latest` returns that same computed snapshot without re-scanning the data on every request:
```json
{"totalReviews": 9, "averageScore": 4.78, "highestScoringPromptId": "832b949f-...", "computedAt": "2026-07-29T04:31:53.445"}
```

**Async notification:** creating a review triggers an `@Async` task (its own dedicated thread pool, not the request thread) that appends a line to `notifications.log` with the reviewer, prompt id, and score. The task deliberately sleeps a few seconds to demonstrate that `POST /reviews` returns immediately regardless:
```bash
time curl -X POST http://localhost:8001/reviews \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"promptId":"<id>","reviewerName":"Saleha","score":5,"feedback":"..."}'
# returns in well under a second; the log line lands ~3s later
```

#### Error responses

Both services use a centralized `@RestControllerAdvice` exception handler and return a consistent JSON error body:

```json
{
  "timestamp": "2026-07-28T12:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Prompt not found with id: <uuid>"
}
```

| Status | When |
|---|---|
| `400 Bad Request` | Invalid request body/params, bad UUID, invalid `sortBy` field, missing/unsupported attachment file |
| `401 Unauthorized` | Missing/invalid/expired JWT, or bad login credentials |
| `404 Not Found` | The requested prompt, review, or attachment does not exist |
| `502 Bad Gateway` | Cloudinary was reached but rejected the request |
| `503 Service Unavailable` | Cloudinary or prompt-service could not be reached at all (connection refused, timeout) |
| `500 Internal Server Error` | Any other unexpected failure |

Creating a review looks up the prompt in prompt-service and stores only its text content in `promptSnapshot` (a `String`), not the full prompt object.

### analytics-service (`/analytics`, proxied at `/api/analytics`)

Read-only analytics computed from the current data in prompt-service and review-service. Requires the same `Authorization: Bearer <token>` as the other two services — analytics-service verifies it independently (PyJWT, same `JWT_SECRET`), it doesn't call back into prompt-service to check tokens.

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | No auth required |
| `GET` | `/analytics/overview` | Totals, average score, attachment count, most-used tag, and the timestamp of the last successful background refresh |
| `GET` | `/analytics/trends?interval=day\|week&days=30` | Prompts created and reviews submitted, bucketed over the requested window |
| `GET` | `/analytics/tags` | Average review score per tag, sorted best to worst |
| `GET` | `/analytics/leaderboard` | Top 5 reviewers by review count, plus top/bottom 5 prompts by average score — only prompts with **2+ reviews** are ranked (`minimumReviewsForRanking` is returned alongside the results so a single 5-star review can't look like the best prompt in the system) |
| `GET` | `/analytics/correlation` | Pearson correlation between prompt content length and average review score, with the sample size it was computed over and an explicit caveat about small-sample reliability |

```bash
curl http://localhost:8002/analytics/overview -H "Authorization: Bearer <token>"
```

**Data collection:** analytics-service has no database of its own. On startup (and on every scheduled refresh) it logs into prompt-service with its own dedicated service-account credentials, then pages through `GET /prompts` and `GET /reviews` — Week 2's `Pageable` endpoints — until every page is retrieved, and loads the results into pandas DataFrames. If a call comes back `401` (expired token), it re-authenticates once and retries automatically.

**Scheduled refresh:** a background job (APScheduler) recomputes the snapshot every `ANALYTICS_REFRESH_INTERVAL_SEC`, so the five endpoints above read from an in-memory snapshot instead of re-fetching from the other two services on every request — the same "expensive work happens in the background" idea as review-service's digest job. If prompt-service or review-service is unreachable when a refresh runs, the failure is logged and the **previous snapshot keeps being served** (surfaced via `lastRefreshError` on `/analytics/overview`) rather than crashing or returning broken data.

**Known simplification:** analytics-service authenticates using the same single login mechanism built for a human user in Week 2, just with its own dedicated username/password (`ANALYTICS_SERVICE_USERNAME`/`PASSWORD`). A real system would typically use a separate mechanism for machine callers (service accounts, client-credentials OAuth, API keys) rather than sharing a login endpoint with a person signing into a UI — see `analytics-service/README.md` for more detail. This is flagged deliberately rather than presented as the ideal design.

See `analytics-service/INSIGHTS.md` for the actual data-science findings (and honest caveats) produced by running these endpoints against real test data.

### ml-service (`/ml`, proxied at `/api/ml`)

Applies two real pretrained models (via the Hugging Face Inference API) to the current data in prompt-service and review-service: semantic search over prompts, and a sentiment-vs-score review quality check. Requires the same `Authorization: Bearer <token>` as the other three services — ml-service verifies it independently, the same way analytics-service does.

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | No auth required |
| `GET` | `/ml/search?q=<text>&top_k=5` | Embeds `q`, ranks every stored prompt by cosine similarity, returns the top-k with their similarity scores |
| `GET` | `/ml/prompts/{id}/similar?top_k=5` | Same ranking, but uses an existing prompt's own stored vector as the query — costs zero Hugging Face calls |
| `GET` | `/ml/review-quality` | Every analyzed review with its sentiment label, confidence, expected sentiment (from its score), and whether/why it was flagged — flagged reviews sort first |

```bash
curl "http://localhost:8003/ml/search?q=summarizing+long+articles&top_k=5" -H "Authorization: Bearer <token>"
```

**Data collection:** ml-service has no database of its own. On startup (and on every scheduled refresh) it logs into prompt-service with its own dedicated service-account credentials, then pages through `GET /prompts` and `GET /reviews` the same way analytics-service does.

**Incremental refresh:** a background job (APScheduler) re-checks every `ML_REFRESH_INTERVAL_SEC`, but only calls Hugging Face for a prompt/review whose content actually changed since the last successful check — everything else reuses its previously computed embedding or sentiment result. This is what keeps the service inside the free tier's hourly rate limit. If Hugging Face rate-limits ml-service partway through a refresh, it stops calling the API for the rest of that cycle, keeps whatever it already computed, logs what was skipped, and picks those items back up on the next scheduled run.

**Cold starts vs. real failures:** the first call to a model that hasn't been used recently can take 30–60s while it loads. ml-service recognizes Hugging Face's "still loading" response specifically and retries after a short wait, rather than treating it as a failure — the same 404-vs-503 discipline Week 1 asked for. See `ml-service/README.md` for the full breakdown of cold-start/rate-limit/permanent-failure handling, plus the documented score-mapping and confidence-threshold rules behind the review quality check.

**Known simplification:** like analytics-service, ml-service authenticates using the same single login mechanism built for a human user in Week 2, just with its own dedicated username/password (`ML_SERVICE_USERNAME`/`PASSWORD`) — flagged deliberately rather than presented as the ideal design.

## Running Locally

### Prerequisites
- Java 17+ and Maven
- Node.js 18+
- Python 3.12+ (analytics-service's pandas dependency does not yet have pre-built installers for the very latest Python releases — 3.12 is the safe choice)
- PostgreSQL running locally, with a `promptdb` database
- Nginx
- A free Hugging Face account and access token (for ml-service) — [huggingface.co/settings/tokens](https://huggingface.co/settings/tokens), no credit card required

### 1. Configure environment variables
All four services read sensitive/environment-specific config from a `.env` file in their own directory. Copy the provided templates and fill in real values:

```bash
cp prompt-service/.env.example prompt-service/.env
cp review-service/.env.example review-service/.env
cp analytics-service/.env.example analytics-service/.env
cp ml-service/.env.example ml-service/.env
```

| Variable | Used by | Purpose |
|---|---|---|
| `SERVER_PORT` | prompt-service, review-service | Port the service listens on (`8000` / `8001`) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | prompt-service | Local PostgreSQL connection |
| `PROMPT_SERVICE_URL` | review-service, analytics-service, ml-service | Base URL for cross-service calls into prompt-service |
| `REVIEW_SERVICE_URL` | analytics-service, ml-service | Base URL for calls into review-service |
| `JWT_SECRET` | All four | Shared signing key for issuing (prompt-service) and validating (all four) tokens — **must be the same value in every `.env` file** |
| `JWT_EXPIRATION_MS` | prompt-service | How long an issued token stays valid |
| `AUTH_USERNAME` / `AUTH_PASSWORD` | prompt-service | Credentials for the single human login account |
| `CLOUDINARY_CLOUD_NAME` / `CLOUDINARY_API_KEY` / `CLOUDINARY_API_SECRET` | prompt-service | Cloudinary account identifier and API credentials |
| `DIGEST_INTERVAL_MS` | review-service | How often the scheduled digest job recomputes review stats |
| `ANALYTICS_SERVICE_PORT` | analytics-service | Port analytics-service listens on (`8002`) |
| `ANALYTICS_SERVICE_USERNAME` / `ANALYTICS_SERVICE_PASSWORD` | analytics-service | Dedicated credentials analytics-service logs in with (see the "known simplification" note above) |
| `ANALYTICS_REFRESH_INTERVAL_SEC` | analytics-service | How often the background job recomputes the analytics snapshot |
| `ML_SERVICE_PORT` | ml-service | Port ml-service listens on (`8003`) |
| `ML_SERVICE_USERNAME` / `ML_SERVICE_PASSWORD` | ml-service | Dedicated credentials ml-service logs in with (same simplification as analytics-service) |
| `HUGGINGFACE_API_TOKEN` | ml-service | Your free Hugging Face access token — never commit this |
| `HF_EMBEDDING_MODEL` / `HF_SENTIMENT_MODEL` | ml-service | Model IDs used for semantic search and the sentiment check |
| `ML_REFRESH_INTERVAL_SEC` | ml-service | How often the incremental refresh job runs |
| `SENTIMENT_CONFIDENCE_THRESHOLD` | ml-service | Minimum model confidence before a sentiment/score disagreement is flagged |

`.env` files are git-ignored — never commit real credentials. Only the `.env.example` templates (placeholder values only) are tracked.

### 2. Start the backend services
```bash
cd prompt-service
./mvnw spring-boot:run       # runs on :8000

cd ../review-service
./mvnw spring-boot:run       # runs on :8001
```

### 3. Start analytics-service and ml-service
```bash
cd analytics-service
python -m venv venv
source venv/bin/activate     # Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --port 8002 --reload
```
```bash
cd ../ml-service
python -m venv venv
source venv/bin/activate     # Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --port 8003 --reload
```
prompt-service and review-service must already be running — both Python services log in and fetch their first snapshot at startup. If they aren't up yet, each logs a warning and keeps retrying on the next scheduled refresh rather than crashing. ml-service's very first refresh is the slowest one, since nothing is cached yet and every prompt/review needs a Hugging Face call — later refreshes are much faster since only changed items get re-sent.

### 4. Build the frontend
```bash
cd prompt-manager-ui
npm install
npm run build                # outputs to prompt-manager-ui/dist
```

### 5. Start Nginx
`nginx.conf` uses a relative `root` (`prompt-manager-ui/dist`), resolved against nginx's prefix
directory — so start it from the repo root and pass that as the prefix:
```bash
# from the prompt-manager/ repo root
nginx -p "$(pwd)" -c nginx/nginx.conf
```

Visit `http://localhost`. Log in, then use the **Analytics** tab to see the dashboard, or the workspace tab for prompts/reviews.

### 6. (Optional) Expose it publicly with ngrok
```bash
ngrok http 80
```

## Integration Test Walkthrough

A single end-to-end sequence exercising every Week 2 feature — auth, attachments, caching, async notifications, and the digest job. Run each step in order (Git Bash / any bash shell; swap in real values as you go):

```bash
# 1. Log in
TOKEN=$(curl -s -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"<AUTH_USERNAME>","password":"<AUTH_PASSWORD>"}' | jq -r .token)

# 2. Create a prompt
PROMPT_ID=$(curl -s -X POST http://localhost:8000/prompts \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Demo Prompt","description":"integration test","content":"hello","tags":"demo","modelTarget":"gpt-4"}' \
  | jq -r .id)

# 3. Attach a file
curl -X POST http://localhost:8000/prompts/$PROMPT_ID/attachment \
  -H "Authorization: Bearer $TOKEN" -F "file=@screenshot.png"

# 4. Fetch it twice — the server log shows CACHE MISS then CACHE HIT
curl http://localhost:8000/prompts/$PROMPT_ID -H "Authorization: Bearer $TOKEN"
curl http://localhost:8000/prompts/$PROMPT_ID -H "Authorization: Bearer $TOKEN"

# 5. Submit a review — response returns immediately; notification lands ~3s later
time curl -X POST http://localhost:8001/reviews \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"promptId\":\"$PROMPT_ID\",\"reviewerName\":\"Saleha\",\"score\":5,\"feedback\":\"great\"}"
cat review-service/notifications.log   # appears a few seconds after step 5 returns

# 6. Check the digest endpoint — totalReviews/averageScore reflect the new review
#    on its next scheduled run (or immediately, if DIGEST_INTERVAL_MS is low)
curl http://localhost:8001/reviews/digest/latest -H "Authorization: Bearer $TOKEN"

# 7. Check analytics-service (polyglot, port 8002) sees the same data over HTTP,
#    verifying the SAME token with its own PyJWT-based check
curl http://localhost:8002/analytics/overview -H "Authorization: Bearer $TOKEN"

# 8. Check ml-service (port 8003) has embedded the new prompt and can find it
#    by meaning, not just by keyword - wait for at least one scheduled refresh
#    (or the startup refresh) before trying this
curl "http://localhost:8003/ml/search?q=greeting&top_k=5" -H "Authorization: Bearer $TOKEN"
curl http://localhost:8003/ml/review-quality -H "Authorization: Bearer $TOKEN"
```

What each step demonstrates:
1. **Auth** — a valid token is required for everything past this point.
2. **Prompt creation** — baseline JPA-backed CRUD.
3. **Cloudinary integration** — the file is uploaded and its `attachmentUrl`/`attachmentPublicId` saved on the prompt.
4. **Caching** — the first `GET` logs `CACHE MISS` (hits Postgres); the second logs `CACHE HIT` (no DB query).
5. **Async notification** — `time` shows the HTTP response returning in well under the ~3s the notification task deliberately sleeps for; `notifications.log` gets a new line shortly after.
6. **Scheduled digest** — the background job (running independently on its own interval) has already picked up the new review by the time you check.
7. **Cross-language auth** — the exact same JWT issued by prompt-service (Java/`jjwt`) is accepted by analytics-service (Python/`PyJWT`), proving the auth scheme isn't tied to Spring Security.
8. **Real ML on real data** — the prompt created in step 2 has been embedded by ml-service's background refresh, so a semantic query for a related concept (not the literal prompt text) can surface it; the review submitted in step 5 has been scored for sentiment and shows up in `/ml/review-quality`, flagged or not, depending on whether its feedback text actually agrees with its score.

(If `jq` isn't available on your system, just copy the `token`/`id` values manually from each response instead of piping through it.)

## Key Engineering Decisions & Challenges

- **Single origin, no CORS in production.** The frontend never calls the backends directly — everything goes through Nginx on the same origin, so the browser never needs a CORS preflight for normal use.
- **Prefix rewriting instead of prefix stripping.** An early version of `nginx.conf` stripped the entire `/api/prompts/` prefix and forwarded to the backend root, which didn't match either service's actual `@RequestMapping`. The fix rewrites `/api/{service}/...` to `/{service}/...`, preserving the path each Spring controller actually expects, and normalizes trailing slashes so `/api/prompts` and `/api/prompts/` both resolve to the exact same backend route.
- **Polyglot persistence.** prompt-service uses Postgres for structured, queryable prompt data; review-service intentionally uses flat JSON files, since reviews are simpler, append-heavy records that don't need relational guarantees.
- **Cache refresh, not just eviction, on writes.** `PUT`/attachment changes use `@CachePut` (not `@CacheEvict`) so the very next `GET` is already a cache hit with the fresh data, rather than forcing one wasted DB round-trip after every update.
- **A dedicated executor for async notifications.** `@Async` without a named executor uses Spring's default `SimpleAsyncTaskExecutor`, which spins up an unbounded new thread per call. A small `ThreadPoolTaskExecutor` bean keeps notification work bounded and separate from other async work the app might add later.
- **Reviews don't have a JPA repository, so pagination/sorting for them is done by hand** over the in-memory list (via reflection, so `sortBy` still accepts any field name) rather than pushed down to a database — a direct consequence of the polyglot-persistence decision above.
- **All Cloudinary/JWT/DB failures are translated at the boundary.** Neither Java service lets a raw SDK or `IOException` leak to the client — each is caught and re-thrown as a specific exception that `@RestControllerAdvice` maps to a meaningful status code (`502`/`503` for Cloudinary, `401` for auth, etc.), the same pattern used for review-service → prompt-service calls in Week 1.
- **A polyglot JWT scheme, not a polyglot auth service.** Rather than adding a fourth, dedicated identity service, analytics-service simply verifies the same signed token itself using PyJWT against the shared `JWT_SECRET` — proving the design decision from Week 2 (a shared-secret, stateless token rather than a central session store) generalizes across languages instead of being incidentally coupled to Spring Security.
- **Snapshot-and-schedule instead of fetch-per-request, applied to a heavier workload.** analytics-service's five endpoints all read from an in-memory pandas snapshot refreshed on an interval, the same "expensive work happens in the background" idea as review-service's digest job — just applied to a full paginated fetch-and-recompute across two services instead of a single in-process aggregation.
- **Graceful degradation on a downstream outage.** If prompt-service or review-service is unreachable when analytics-service's scheduled refresh runs, the failure is logged and the *previous* successful snapshot keeps being served — surfaced via `lastRefreshError` on `/analytics/overview` — rather than the analytics endpoints crashing or silently returning empty/wrong data.
- **Incremental refresh instead of recompute-everything, once a real quota is involved.** analytics-service's snapshot is cheap enough to fully recompute every cycle. ml-service can't do that — each item costs a real, rate-limited third-party API call — so its refresh diffs `content`/`updatedAt` (prompts) and `feedback`/`score` (reviews) against what was already embedded/scored, and only calls Hugging Face for what actually changed. This is the same "don't do more work than the data requires" instinct as the snapshot pattern above, pushed one step further because here the cost isn't just CPU time, it's a metered external resource.
- **Three outcomes, not one, for a failed Hugging Face call.** A naive client treats every non-200 response as "the call failed, log and move on." That collapses three genuinely different situations that need different responses: a model *cold-starting* (HTTP 503 + a loading body) is worth waiting on and retrying; a *rate limit* (HTTP 429) means stop calling entirely for the rest of this cycle, not retry faster; anything else (a real 5xx, a 404 for a model that moved) is a permanent failure for that call and shouldn't be retried in a loop. `ml-service/app/services/hf_client.py` raises three distinct exception types so callers can react to each correctly instead of branching on status codes ad hoc.
- **A documented, defensible rule instead of a hidden one, for "does this review look wrong."** Rather than flagging every sentiment/score mismatch, the review quality check only flags a mismatch when the model is confident about it (`SENTIMENT_CONFIDENCE_THRESHOLD`, default `0.75`) and treats a middling 3/5 score as having no expected sentiment at all, rather than forcing it into "positive" or "negative." Both choices are judgment calls, not facts, so they're written down and justified in `ml-service/README.md` rather than left implicit in the code.

## Git Branching Strategy

This project follows a lightweight, GitHub-flow-style branching model:

- **`main`** is always the stable, working state of the project. Nothing is committed to it directly.
- All work happens on a short-lived **`feature/<short-description>`** branch (e.g. `feature/review-service`, `fix/evaluator-feedback`), branched off the latest `main`.
- Work is merged back into `main` via a **pull request** (merge commit), not a direct push, so history shows what changed and why as a discrete unit rather than as loose commits on `main`.
- Branches are deleted after merging to keep the branch list clean.

## Author

**Saleha Yasir**
Software Engineering Intern
