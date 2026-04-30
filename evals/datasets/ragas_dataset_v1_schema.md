# Ragas Dataset v1 Schema (Step 4)

Use this schema for each JSON object line in `evals/datasets/ragas_dataset_v1.jsonl`.

## Required fields

- `id` (string): unique case id (e.g. `rg_001`)
- `user_input` (string): original user question
- `response` (string): chatbot answer generated for that question
- `retrieved_contexts` (array of strings): chunks/contexts actually retrieved for this answer
- `reference` (string): expected target answer used as evaluator reference

## Strongly recommended fields

- `reference_contexts` (array of strings): gold supporting contexts for recall/precision metrics

## Why this exists

DeepEval in Step 3 checks output quality in CI. Ragas in Step 4 diagnoses *why* quality changed:

- faithfulness: is answer grounded in retrieved contexts
- answer relevancy: does answer address the question
- context precision: are retrieved contexts mostly relevant
- context recall: did retriever include needed context

## Rules

1. Keep `retrieved_contexts` as the real chunks returned by your retriever (from logs/traces), not rewritten summaries.
2. Keep each context string concise and directly from source text.
3. Keep one concern per testcase to make metric changes interpretable.
4. Start with 20-30 high-value cases before scaling to 50-100.
5. For business-critical cases, manually verify metrics against human judgment.
