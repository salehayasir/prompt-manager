# ml-service

A Python/FastAPI service that applies two real pretrained models from the
Hugging Face Inference API to prompt-manager's own data: semantic search
over prompts (find by meaning, not exact words) and a review quality check
(does a review's written feedback actually agree with its numeric score).
Like analytics-service, it has no database of its own - it pulls prompts
and reviews over HTTP, the same way any other client would.

## Setup

```bash
cd ml-service
python -m venv venv
source venv/bin/activate      # Windows: venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env          # then fill in real values, including a free
                               # Hugging Face token from
                               # https://huggingface.co/settings/tokens
```

Required environment variables (see `.env.example`):

| Variable | Purpose |
|---|---|
| `ML_SERVICE_PORT` | Port this service runs on (default 8003) |
| `JWT_SECRET` | Must match the other three services exactly |
| `PROMPT_SERVICE_URL` / `REVIEW_SERVICE_URL` | Base URLs for the two Java services |
| `ML_SERVICE_USERNAME` / `ML_SERVICE_PASSWORD` | Credentials ml-service logs in with |
| `HUGGINGFACE_API_TOKEN` | Your free Hugging Face access token |
| `HF_EMBEDDING_MODEL` | Model used for semantic search embeddings |
| `HF_SENTIMENT_MODEL` | Model used for the review quality sentiment check |
| `ML_REFRESH_INTERVAL_SEC` | How often the incremental refresh runs |
| `SENTIMENT_CONFIDENCE_THRESHOLD` | Minimum model confidence before a disagreement is flagged |

## Running

```bash
uvicorn app.main:app --port 8003 --reload
```

prompt-service and review-service must both be running and reachable at the
URLs configured above. The first refresh (at startup) is the expensive one,
since nothing is cached yet - every prompt and every piece of review
feedback needs a Hugging Face call. After that, only new/changed items cost
a call (see "Incremental refresh" below).

## Endpoints

All endpoints below except `/health` require `Authorization: Bearer <token>`.

- `GET /health` - no auth required
- `GET /ml/search?q=<text>&top_k=5` - embeds the query, ranks stored prompts by cosine similarity, returns the top-k with scores
- `GET /ml/prompts/{id}/similar?top_k=5` - same idea, but reuses that prompt's own stored vector as the query (no fresh Hugging Face call needed)
- `GET /ml/review-quality` - every analyzed review with its sentiment label, confidence, expected direction, and whether/why it was flagged. Flagged reviews sort first.

## Score-mapping and confidence-threshold rule

The review quality check needs a rule for what numeric score "should" pair
with what sentiment. This is the rule implemented here (see
`app/services/store.py::_expected_sentiment`):

| Score | Expected sentiment |
|---|---|
| 4-5 | positive |
| 1-2 | negative |
| 3 | neutral - **never flagged** |

A 3/5 is treated as genuinely ambiguous rather than forced into "positive"
or "negative" - a reviewer giving a middling score with negative-sounding
feedback ("it's fine but the formatting is annoying") isn't really
contradicting themselves the way a 5/5 paired with angry feedback would be.
Reviews with empty feedback text are skipped entirely, since there's
nothing for a sentiment model to score.

A review is flagged only when **both**:
1. The model's sentiment label disagrees with the expected direction from
   its score (a 5/5 with a model-detected NEGATIVE label, or a 1/5 with a
   model-detected POSITIVE label), **and**
2. The model's confidence in that label is at or above
   `SENTIMENT_CONFIDENCE_THRESHOLD` (default `0.75`).

The threshold exists because sentiment models are frequently unsure on
short or lukewarm text ("it's okay I guess"), and a 55%-confidence
disagreement is much weaker evidence than a 98%-confidence one. `0.75` was
chosen as a middle ground: high enough to filter out genuinely uncertain
calls, low enough to still catch clear mismatches without requiring
near-total model certainty. This is a judgment call, not a fact - a
stricter reviewer of this service might reasonably argue for a higher bar
like `0.9` to further cut down on false positives, at the cost of missing
some real mismatches.

## Incremental refresh (Part 5)

On each scheduled run (`ML_REFRESH_INTERVAL_SEC`), ml-service pulls the
current prompts and reviews and compares each one against what it already
has in memory:

- A prompt is re-embedded only if it's new, or its `content` or `updatedAt`
  has changed since the last successful embedding. Otherwise the existing
  vector is reused and no Hugging Face call is made.
- A review is only re-scored for sentiment if it's new, or its `feedback`
  text (or `score`) has changed since the last check.
- Prompts/reviews that were deleted upstream have their cached
  vector/sentiment entry dropped on the next refresh.

If the Hugging Face API returns a rate-limit response (`429`) partway
through a refresh, ml-service stops calling it for the **rest of that
cycle** (both remaining prompts and remaining reviews), keeps whatever it
already successfully computed, and logs how many items were skipped. Those
skipped items are picked up automatically on the next scheduled run, since
they'll still look "changed" (their content hasn't been embedded yet).

## Handling the Hugging Face Inference API honestly

See `app/services/hf_client.py` for the full implementation. The two
behaviors the assignment calls out are handled as three distinct outcomes,
not lumped into one "it failed" case:

- **Cold start (HTTP 503 with a "loading" body)** - the client waits (using
  the API's own `estimated_time` when given, capped at 65s) and retries, up
  to 2 retries, before giving up on that one item for this cycle.
- **Rate limited (HTTP 429)** - treated as "stop calling HF for the rest of
  this refresh cycle," not as "retry immediately" - retrying instantly
  against an hourly quota wouldn't help.
- **Anything else non-2xx** (a genuine 503 without a loading body, 404, 500,
  etc.) - raised as a permanent failure for that call, logged, and skipped;
  not retried in a loop.

**API stability note:** this was built against Hugging Face's current
router-based endpoint
(`https://router.huggingface.co/hf-inference/models/{model_id}`). Hugging
Face has changed this base URL more than once as they've introduced
"Inference Providers." If model calls start failing outright (not with a
loading/rate-limit response, but immediately), check
https://huggingface.co/docs/inference-providers for the current URL format
- per the assignment itself, this is expected to shift over time.

## Known simplification: shared login account

Like analytics-service, ml-service authenticates to prompt-service using
the **same single login endpoint and account mechanism** built in Week 2
for a human user, just with its own dedicated username/password
(`ML_SERVICE_USERNAME` / `ML_SERVICE_PASSWORD`). This is a known shortcut,
not a recommended design - a real system would typically use a separate
mechanism for machine callers (service-account credentials, client
credentials OAuth, API keys, or mTLS) rather than sharing a human login
endpoint and token shape.

## Data collection and resilience

- All data is fetched by paging through prompt-service's `/prompts` and
  review-service's `/reviews` until every page is retrieved.
- If either Java service is unreachable when a scheduled refresh runs, the
  failure is logged and the **previous data keeps being served** rather
  than crashing the service or wiping out what was already computed.
  `GET /ml/review-quality` surfaces `lastRefreshError` when this happens.
- If a call to prompt-service/review-service returns 401 (token expired),
  ml-service re-authenticates once and retries automatically.
