# Insights Write-Up

Findings below are pulled directly from `/analytics/overview`, `/analytics/tags`,
`/analytics/trends`, `/analytics/leaderboard`, and `/analytics/correlation`
against my own test data (14 prompts, 9 reviews) as of 2026-08-09.

## Finding 1: Tag performance — the numbers look good, but the sample is too thin to trust

`/analytics/tags` shows `spotify` and `study` tied at the top with a 5.0
average, followed by `java`/`spring` at 4.8 and `summary` at 4.5. On the
surface that looks like a clear winner.

It isn't one. Cross-referencing with `/analytics/leaderboard` and
`/analytics/overview` shows that **only 3 of my 14 prompts (about 21%) have
ever been reviewed at all** — "spotify" (2 reviews), "Java Teacher" (5
reviews), and "Summarize Text" (2 reviews) — and every tag with a score
traces back to one of those same 3 prompts. The other 11 prompts, and every
other tag in the system, have zero reviews and simply don't show up in the
ranking.

On top of that, the reviewer list (`topReviewers`) shows `Saleha` (5
reviews) and `saleha` (2 reviews) as separate entries — almost certainly the
same person, split by inconsistent capitalization in the test data. If
merged, one reviewer authored 7 of the 9 reviews in the entire system. So
even the "best" tag's score isn't really an aggregate opinion — it's
mostly one person's opinion, recorded during testing.

**Conclusion:** `spotify`/`study` cannot honestly be called the best-performing
tag. There isn't enough independent review coverage, across enough prompts
or enough distinct reviewers, to say anything meaningful about tag quality
yet. `Java Teacher` (5 reviews, 4.8 avg) is the single most-reviewed prompt
and probably the most *credible* individual data point in the system, but
even that is a sample size of 5.

## Finding 2: Submission volume — no real trend, just testing bursts

`/analytics/trends` shows prompt creation on only 6 distinct days across a
~4-week window (Jul 13, 14, 20, 28, 29, and Aug 9), and reviews on only 4
of those same days (Jul 13, 14, 28, 29) — reviews never appear on a day
without a matching burst of prompt creation. That pattern matches
individual internship work sessions, not organic, spread-out usage.

**Conclusion:** there is no submission trend to report, upward or downward.
The data doesn't have enough independent time points to distinguish "usage
is growing" from "I did some testing on a few different days." Concluding
a trend from 6 data points clustered into 4 bursts would be presenting
noise as a signal.

## Finding 3: Correlation between prompt length and review score — not trustworthy

`/analytics/correlation` reports a coefficient of **0.9999** on a
**sample size of 3** — the same 3 prompts referenced in Finding 1.

A near-perfect correlation on only 3 data points is a red flag, not a
result. With that few points, an extreme correlation coefficient is common
by pure chance and carries almost no statistical weight — the endpoint's
own caveat message says as much. I would need review data spread across
dozens of prompts, from multiple independent reviewers, before this number
would be worth acting on.

**Conclusion:** I do not trust this correlation, and would not use it to
make any claim about whether prompt length affects review score. The
honest reading is "insufficient data," not "content length matters."

---

## Overall takeaway

All three findings point to the same root cause: **the system currently has
far more prompts (14) than reviewed prompts (3), and far more reviews (9)
than independent reviewers (effectively 2-3, once the casing duplicate is
accounted for).** Every analytics endpoint is working correctly and
computing exactly what it should — the limitation here is data volume, not
the code. Any of these "findings" would become meaningfully more
trustworthy with review coverage across most prompts and contributions
from several distinct reviewers, rather than a small set of prompts
reviewed mostly by one person during development.
