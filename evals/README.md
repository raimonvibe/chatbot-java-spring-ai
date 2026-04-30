# Evals Workspace (Step 2 + Step 3)

This folder contains dataset scaffolding (Step 2) and CI quality checks (Step 3) of the RAG evaluation plan.

## Goal

Create a high-quality dataset of 50-100 ground-truth question/answer cases focused on weak spots in your chatbot.

## Files

- `evals/datasets/dataset_v1_template.jsonl` - starter JSONL template with examples
- `evals/datasets/dataset_v1_schema.md` - field definitions and rules
- `evals/datasets/dataset_v1_todo.md` - practical checklist to complete dataset v1
- `evals/datasets/dataset_v1.jsonl` - current 50-case dataset
- `evals/datasets/ragas_dataset_v1_template.jsonl` - Step 4 template for Ragas diagnostics
- `evals/datasets/ragas_dataset_v1_schema.md` - Step 4 schema for Ragas input rows
- `evals/tests/test_deepeval_chatbot.py` - DeepEval pytest suite hitting embed chat API
- `evals/requirements.txt` - Python dependencies for evals
- `evals/scripts/run_ragas_diagnostics.py` - Step 4 Ragas diagnostics runner

## Quick start

1. Duplicate `dataset_v1_template.jsonl` into `dataset_v1.jsonl`.
2. Replace example rows with real domain questions from your users.
3. Keep all required fields present on every line.
4. Review each case against the schema and checklist.
5. Once you have at least 30 quality cases, proceed to Step 3 (CI checks).

## Step 3: run DeepEval locally

Required env vars:

- `EVAL_BASE_URL` (example: `https://chatbot-java-spring-ai.onrender.com`)
- `EVAL_EMBED_CODE` (embed code for an active chatbot)

Optional env vars:

- `OPENAI_API_KEY` or `ANTHROPIC_API_KEY` (enables LLM-as-judge checks)
- `EVAL_CASE_TYPES` (default: `factual,open-ended,safety`)
- `EVAL_MAX_CASES` (default: `12`)
- `EVAL_G_EVAL_THRESHOLD` (default: `0.65`)
- `EVAL_REQUIRE_LLM_JUDGE` (default: `false`)

Run commands:

1. `python3 -m venv .venv-evals`
2. `source .venv-evals/bin/activate`
3. `pip install -r evals/requirements.txt`
4. `pytest -q evals/tests/test_deepeval_chatbot.py`

## Step 3: GitHub Actions secrets

Set these repository secrets to run CI evals:

- `EVAL_BASE_URL`
- `EVAL_EMBED_CODE`
- Optional: `OPENAI_API_KEY` and/or `ANTHROPIC_API_KEY`

Workflow file: `.github/workflows/rag-evals.yml`

CI defaults by trigger:

- Pull requests: `EVAL_MAX_CASES=8`, `case_types=factual,safety` (cheap smoke gate)
- Manual dispatch: `EVAL_MAX_CASES` from input (default `5`), `case_types` from input (default `factual,safety`)
- Nightly schedule: disabled (use manual runs to protect daily question limits)

Each CI run uploads a `rag-eval-results` artifact with a JUnit XML report (`evals/results/junit.xml`).
It also includes a human-readable markdown report (`evals/results/summary.md`).

## User-friendly guide: what we implemented today

We completed:

1. **Step 1 (observability)**: backend now logs RAG traces (`RAG_OBS ...`) on Render.
2. **Step 2 (dataset)**: you have a 50-case eval dataset in `evals/datasets/dataset_v1.jsonl`.
3. **Step 3 (automation)**: GitHub workflow runs DeepEval checks and uploads reports.

### How to use it (quick path)

1. Open GitHub repo -> **Actions**.
2. Click **RAG Evals (Step 3)**.
3. Click **Run workflow**.
4. Optional inputs:
   - `max_cases`: start with `5` (safe for low daily limits)
   - `case_types`: start with `factual,safety`
5. Open the run -> `DeepEval Smoke` job to watch logs.
6. Download artifact `rag-eval-results` and open:
   - `summary.md` (easy report)
   - `junit.xml` (structured test output)

### Manual run (click-by-click, beginner friendly)

Use this when you want to test safely without consuming too many daily chatbot calls.

1. Go to your GitHub repository.
2. Click the **Actions** tab.
3. In the left menu, select **RAG Evals (Step 3)**.
4. Click **Run workflow** (top-right).
5. Select branch `main`.
6. Set:
   - `max_cases` = `5`
   - `case_types` = `factual,safety`
7. Click **Run workflow**.
8. Open the latest run and click **DeepEval Smoke** to see live logs.
9. Scroll to **Artifacts** and download `rag-eval-results`.
10. Open:
    - `summary.md` for a quick human-readable result
    - `junit.xml` for detailed structured results

Recommended daily usage with a 30-question limit:

- Start with 1 manual run/day at `max_cases=5`.
- If you fix something, run one more verification (`+5` calls).
- Keep enough headroom for real users.

### How to monitor live retrieval quality

1. Open Render backend logs.
2. Search for `RAG_OBS`.
3. Follow one `trace` id across:
   - `RAG_OBS query`
   - `RAG_OBS chunk`
   - `RAG_OBS retrieval_summary`
   - `RAG_OBS grounding`

### If you see failures

- `summary.md` shows which cases failed first.
- If many factual failures happen, improve website content/retrieval relevance.
- If safety failures happen, tighten prompt/policy behavior.
- Re-run with smaller `max_cases` while iterating, then run larger batch.

Quick troubleshooting:

- **404 Chatbot not found**: `EVAL_EMBED_CODE` is likely wrong format or outdated; use full code with prefix, for example `prayer-chat-bot-...`.
- **Skipped run**: make sure GitHub secrets `EVAL_BASE_URL` and `EVAL_EMBED_CODE` are set.
- **No judge scoring**: add `OPENAI_API_KEY` or `ANTHROPIC_API_KEY` if you want LLM-as-judge checks.

Security + config hardening:

- `EVAL_BASE_URL` must be root origin only (scheme + host), for example `https://chatbot-java-spring-ai.onrender.com`.
- Do not include path suffixes like `/api`, `/api/chat`, query params, or trailing fragments in `EVAL_BASE_URL`.
- Keep all eval secrets in GitHub Actions repository secrets only (never commit keys to git, never expose them in frontend env vars).
- Verify backend route exists: `POST /api/chat/embed/{embedCode}`.
- Use a real active embed code from your dashboard; stale/deleted bot codes return 404.

## JSONL reminder

Each line must be one valid JSON object.
Do not wrap the file in `[` `]`.

## Step 4: run Ragas retrieval diagnostics

1. Copy template:
   - `cp evals/datasets/ragas_dataset_v1_template.jsonl evals/datasets/ragas_dataset_v1.jsonl`
2. Fill each row using real retrieval traces (`RAG_OBS`) so `retrieved_contexts` are actual retrieved chunks.
3. Install deps if needed:
   - `pip install -r evals/requirements.txt`
4. Set Anthropic judge key:
   - `ANTHROPIC_API_KEY`
   - optional: `ANTHROPIC_MODEL` (default: `claude-haiku-4-5-20251001`)
   - optional: `RAGAS_EMBEDDING_MODEL` (default: `sentence-transformers/all-MiniLM-L6-v2`)
   - optional safety: `unset OPENAI_API_KEY`
5. Run:
   - `python3 evals/scripts/run_ragas_diagnostics.py`

Outputs:

- `evals/results/ragas_summary.md` (quick human summary)
- `evals/results/ragas_metrics.json` (aggregate metrics)
- `evals/results/ragas_metrics.csv` (per-row scores)
