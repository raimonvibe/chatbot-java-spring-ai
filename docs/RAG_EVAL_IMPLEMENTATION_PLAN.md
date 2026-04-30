# 📘 RAG Evaluation and Observability Blueprint

This document is a professional implementation plan for introducing robust RAG quality evaluation in this project with:

- 🧠 **Ragas** for retrieval and grounding quality metrics
- ✅ **DeepEval** for automated CI regression testing
- 📊 **Braintrust** for production tracing, scoring, and monitoring

It incorporates the failure-mode approach from your three slides:

1. Instrument retrieval first.
2. Build evals around discovered weaknesses.
3. Score automatically with the right metric per answer type.

---

## 🌱 Beginner quick start (no scope changes)

If you are new to RAG evaluation, follow this order first:

1. Turn on retrieval logging (query, chunks, scores, final answer).
2. Build 20 simple test questions from real user requests.
3. Run those questions through your chatbot API and save outputs.
4. Add basic DeepEval checks in CI for regressions.
5. Add Ragas metrics to understand retrieval quality gaps.
6. Add Braintrust tracing in production for ongoing monitoring.

This is the same plan as below, just expressed as a first-time setup path.

---

## 🗂️ Plain-language glossary

- 📚 **RAG**: the chatbot first retrieves documents/chunks, then generates an answer.
- 🧩 **Chunk**: a small piece of source text stored for retrieval.
- 🔎 **Faithfulness**: whether answer claims are supported by retrieved context.
- 🎯 **Context recall**: whether the retriever found the needed context.
- 🧹 **Context precision**: whether retrieved chunks are relevant and not noisy.
- ⚖️ **LLM-as-judge**: using an LLM to score answer quality against a rubric.
- 🧪 **Exact match**: strict deterministic check for factual answers.
- 📝 **Telemetry**: logs/traces that describe what the pipeline did.
- ⏱️ **SLO**: service-level objective, such as max latency target.
- 🔄 **Reranker**: model that reorders retrieved chunks to improve relevance.

---

## 🎯 Executive objective

Move from "it seems to work" to a measurable quality loop that answers:

- 🔍 Did retrieval fetch the right context?
- 🧠 Did the model use that context faithfully?
- 🚦 Are changes introducing regressions before merge?
- 📈 Is production quality stable release over release?

---

## 💡 Guiding principles (outside-the-box but practical)

- 🛠️ **Failure-mode first**: do not start with metrics dashboards; start by logging where the pipeline fails.
- 📥 **Retrieval before generation**: most RAG errors come from missing or noisy context, not from prompt wording.
- ⚖️ **Mixed judging strategy**: use deterministic checks for factual outputs and LLM-as-judge for open-ended outputs.
- 🧪 **Tiered evals**: small fast suite on PR, larger suite nightly, full diagnostics for experiments.
- 🔁 **Production-to-test feedback**: turn real production failures into new dataset examples weekly.

---

## ✅ What "good" looks like

Initial targets (to calibrate after baseline):

- 🔎 Faithfulness >= 0.80
- 🧠 Answer relevancy >= 0.85
- 🧹 Context precision >= 0.75
- 🎯 Context recall >= 0.75
- ⏱️ P95 latency within agreed SLO

For regulated or high-risk flows, use stricter thresholds and manual review.

---

## 🏗️ System design: measurement architecture

### 1) 📥 Retrieval instrumentation (must-have first step)

For every request, log:

- 🧾 `query_text` and query metadata
- 📄 top-k retrieved chunks (`chunk_id`, `document_id`, rank)
- 📐 similarity scores per chunk
- 🔁 optional reranker scores (if reranking is enabled)
- 🧠 prompt/context payload sent to model
- 💬 final answer
- 🔗 citation or grounding markers (if available)

Why: this enables root-cause analysis for failures like "correct answer impossible because key chunk was never retrieved."

### 2) 🧪 Eval harness centered on failure modes

Build a `50-100` case dataset focused on weak spots:

- ❓ ambiguous questions
- 🧭 multi-hop questions
- 🗣️ lexical mismatch/synonym cases
- 📚 long-context questions
- 🛡️ policy/safety boundary cases

Each testcase should include:

- ❔ question
- ✅ expected answer or rubric
- 📎 expected supporting source references (if known)
- 🏷️ testcase type (`factual`, `open-ended`, `safety`)
- ⚠️ risk tag (`critical`, `standard`, `low`)

### 3) 📊 Scoring strategy

- 🧪 **Factual outputs**: exact match or structured field checks.
- ⚖️ **Open-ended outputs**: LLM-as-judge metrics with strict prompts/rubrics.
- 🧠 **RAG diagnostics**: Ragas metrics for faithfulness, context precision, context recall, answer relevancy.

---

## 🧰 Tool roles in this project

## 🧠 Ragas (diagnose retrieval and grounding)

Use for offline diagnostics when tuning retriever/chunking/reranker/prompt.

Best use:

- 🔍 compare retrieval revisions
- ⚖️ identify recall vs precision tradeoffs
- ✅ verify answer grounding in context

## ✅ DeepEval (enforce quality in CI)

Use as test framework in pull requests and nightly checks.

Best use:

- 🧪 pytest-style tests around API outputs
- 🚦 threshold-based pass/fail gates
- 🌙 smoke suite on PR + full suite on schedule

## 📊 Braintrust (observe and score production)

Use for live tracing and online scoring of production traffic.

Best use:

- 🔗 end-to-end spans for retrieval -> prompt -> generation
- 🚨 quality regressions that do not show up in latency/error metrics
- 📈 sampling-based scoring at scale

---

## 🗺️ Recommended rollout plan

## 🧱 Phase 0 - Baseline and telemetry contract (1-2 days)

- 📝 Define standard log schema for retrieval + generation events.
- ❄️ Freeze a baseline model/prompt/retriever config.
- 🔎 Run 20-30 known queries and verify logs are complete.

Deliverable: baseline trace set with complete retrieval metadata.

## 🚦 Phase 1 - CI quality gate (DeepEval) (2-4 days)

- 📚 Create initial dataset (`30-50` high-value cases).
- 🧪 Implement API-driven DeepEval tests.
- Add CI workflow:
  - PR: smoke tests
  - Nightly: full suite
- ⚠️ Set temporary thresholds and fail on major regressions.

Deliverable: pull requests get automated quality pass/fail signal.

## 🔬 Phase 2 - Retrieval diagnostics (Ragas) (2-4 days)

- 🥇 Add gold supporting references for selected cases.
- 📊 Run Ragas reports per experiment branch.
- 📈 Track trends by retriever setting:
  - chunk size/overlap
  - top-k
  - hybrid search on/off
  - reranker on/off

Deliverable: evidence-backed retriever tuning decisions.

## 🛰️ Phase 3 - Production scoring and monitoring (Braintrust) (3-5 days)

- 🧵 Instrument production traces and metadata.
- ⚙️ Configure online scoring with sampling.
- 🔔 Add alerts for:
  - faithfulness drop
  - relevancy drop
  - latency spikes
- 🔁 Feed failed traces back into dataset weekly.

Deliverable: closed-loop continuous improvement from real traffic.

---

## ✅ Step-by-step checklist (beginner execution)

Use this checklist in order. It does not change the plan, only makes execution easier.

### 1️⃣ Step 1 - Instrument retrieval (Phase 0)

- [ ] Log each incoming query.
- [ ] Log top-k chunks with rank and score.
- [ ] Log final prompt/context sent to the model.
- [ ] Log final answer and request latency.
- [ ] Verify logs are visible for at least 20 manual test queries.

### 2️⃣ Step 2 - Create dataset v1 (Phase 1 input)

- [ ] Collect 30-50 real user questions.
- [ ] Mark each as `factual`, `open-ended`, or `safety`.
- [ ] Add expected answer text or short judging rubric.
- [ ] Add risk tag `critical`, `standard`, or `low`.

### 3️⃣ Step 3 - Add CI quality checks (Phase 1)

- [ ] Create DeepEval tests that call the chatbot API.
- [ ] Add thresholds for faithfulness/relevancy.
- [ ] Run smoke tests on pull requests.
- [ ] Run full eval suite nightly.

### 4️⃣ Step 4 - Diagnose retrieval quality (Phase 2)

- [ ] Add expected supporting references for key cases.
- [ ] Run Ragas and export results.
- [ ] Compare baseline vs updated retriever settings.
- [ ] Document which change improved recall/precision.

### 5️⃣ Step 5 - Monitor production quality (Phase 3)

- [ ] Enable Braintrust tracing for retrieval and generation spans.
- [ ] Configure scoring rule sampling.
- [ ] Add alerts for quality drops and latency spikes.
- [ ] Add failed production traces to eval dataset each week.

---

## 🤝 Responsibilities

## 👤 What you need to do (business and governance owner)

1. Curate first `50-100` representative questions.
2. Define what counts as "correct" for each scenario.
3. Confirm privacy/redaction policy for logs and traces.
4. Provide secrets for local and CI environments.
5. Approve release thresholds for critical and non-critical paths.
6. Review failed evals weekly and prioritize fixes.

## 🛠️ What I can implement in this repository (execution owner)

1. Create `evals/` scaffolding and dataset templates.
2. Add DeepEval test modules and runner scripts.
3. Add Ragas batch runner and report generation.
4. Add CI workflows for PR and scheduled runs.
5. Add documentation for local setup and troubleshooting.
6. Add Braintrust instrumentation checklist and integration notes.

---

## 🌟 Advanced improvements to consider

- 🔀 **Hybrid retrieval**: combine vector search with BM25 lexical retrieval.
- 🧠 **Cross-encoder reranking**: improve top-k ordering quality before generation.
- 🔗 **Citation mode**: force answer citations to aid grounding checks.
- 🐤 **Canary evals**: run small shadow eval set on each deploy.
- 🛡️ **Risk-based gating**: stricter thresholds for critical business journeys.

These align with your slide recommendations and typically produce measurable gains.

---

## ⚠️ Risks and mitigations

- 📉 **Metric instability**
  - keep temperature low for judged tests
  - rerun borderline failures with multiple samples
- 💸 **CI cost growth**
  - use smoke suite on PR, full suite nightly
- 🧊 **Dataset staleness**
  - add new failures from production every week
- 🔒 **Privacy/compliance risk**
  - redact PII and avoid storing forbidden payloads

---

## 🏁 Definition of done

The initiative is complete when:

- 🧾 telemetry captures retrieval and generation metadata per query
- 📚 eval dataset v1 is in place and reviewed
- ✅ DeepEval gates run in CI and enforce thresholds
- 📊 Ragas compares at least two retriever configurations with trend reports
- 📡 Braintrust receives traces and online scoring in production
- 🔁 there is a recurring review loop that turns failures into new tests

---

## 🔎 Research references

- [Ragas metrics overview](https://docs.ragas.io)
- [DeepEval documentation](https://deepeval.com/docs/introduction)
- [Braintrust scoring production traces](https://www.braintrust.dev/docs/observe/score-online)
- [Braintrust tracing LLM calls](https://www.braintrust.dev/docs/instrument/trace-llm-calls)

These references were used to align this plan with current framework guidance and best practices.

---

## 📅 One-week starter rhythm (beginner friendly)

- **Day 1**: complete Step 1 and verify logs with 20 test queries.
- **Day 2**: complete Step 2 and freeze dataset v1.
- **Day 3**: complete Step 3 and enable PR smoke gate.
- **Day 4**: complete Step 4 and publish first Ragas comparison.
- **Day 5**: complete Step 5 and enable production scoring sample.

If this feels too much at once, do Days 1-3 first, then continue next week.

---

## 🧭 How to read Step 1 logs on Render

After deployment, open Render logs and search for `RAG_OBS`.

Expected log lines per request:

- `RAG_OBS query` → user query + trace id
- `RAG_OBS chunk` → retrieved chunk details (rank, source, score, title, url, snippet)
- `RAG_OBS retrieval_summary` → number of docs retrieved/logged
- `RAG_OBS grounding` → heuristic count of whether answer referenced retrieved docs

Quick interpretation guide:

- ✅ Good retrieval signal:
  - `retrieval_summary totalRetrieved` is non-zero
  - top chunks are clearly relevant to the query
- ⚠️ Possible retrieval miss:
  - irrelevant chunk titles/snippets in top ranks
  - `totalRetrieved` is low on questions that should have rich context
- ⚠️ Possible grounding issue:
  - `grounding referencedRetrievedDocs=0` repeatedly on factual/site-specific questions
  - answer preview looks generic while chunk snippets are specific

Render log workflow:

1. Trigger a test chat question.
2. Copy the `trace` id from `RAG_OBS query`.
3. Filter logs by that trace id.
4. Inspect chunk ranking and score patterns.
5. Compare final answer preview against retrieved snippets.

Starter test prompts for manual verification:

- "What does this website offer?"
- "Tell me your pricing options."
- "Who is this site for?"
- One niche question with domain-specific terms from your content.

These checks are enough to validate Step 1 before moving to dataset and CI harness work.

---

## 📌 Progress update (Apr 30, 2026)

Current status after today’s implementation and validation:

- ✅ **Step 1 complete**: retrieval observability is implemented (`RAG_OBS query/chunk/retrieval_summary/grounding`) and usable in Render logs.
- ✅ **Step 2 complete**: dataset scaffolding exists and dataset v1 is present in `evals/datasets/`.
- ✅ **Step 3 complete**: DeepEval smoke workflow is active in GitHub Actions with manual/PR runs and artifact output.
- ✅ **Step 4 runner implemented**: Ragas diagnostics script, schema, templates, and documentation are in place.
- ✅ **Step 4 first run succeeded locally**: outputs generated in `evals/results/` (`ragas_summary.md`, `ragas_metrics.json`, `ragas_metrics.csv`).
- ✅ **Security hardening completed**:
  - Anthropic-only Step 4 path enforced
  - model default aligned with backend (`claude-haiku-4-5-20251001`)
  - local eval outputs/dataset/venv added to `.gitignore`

### 📊 Latest evaluation snapshot (first local Step 4 run)

- Cases evaluated: `2`
- Faithfulness: `1.00`
- Answer relevancy: `0.3832`
- Context precision: `0.50`
- Context recall: `0.50`

Interpretation:

- Grounding is strong on current examples (high faithfulness).
- Retrieval quality conclusions are **not stable yet** because sample size is too small.
- One "unknown/not found" style case likely under-scored relevancy/precision/recall due to reference formatting.

## ▶️ Next steps (priority order)

1. Expand Step 4 dataset from `2` to at least `20-30` real cases from `RAG_OBS` traces.
2. Normalize reference answers for "missing info" cases (concise expected answer, no rubric text inside `reference` field).
3. Re-run Ragas and compare aggregate metrics against this baseline.
4. Run two retriever experiments and compare with Ragas:
   - baseline config (current)
   - increased top-k and/or reranker enabled
5. Promote stable thresholds for ongoing gate decisions once metrics are based on 20+ cases.

**Definition of immediate success for next iteration:**

- Ragas run with >= `20` cases
- reproducible metrics report saved to `evals/results/`
- one documented retriever tuning decision backed by metrics
