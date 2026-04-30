# Dataset v1 Schema

Use this schema for each JSON object line in `dataset_v1.jsonl`.

## Required fields

- `id` (string): unique testcase id, e.g. `tc_001`
- `question` (string): user prompt/question
- `case_type` (string): one of `factual`, `open-ended`, `safety`
- `risk` (string): one of `critical`, `standard`, `low`
- `expected_answer` (string): ideal answer or concise target answer
- `judge_rubric` (string): what a good answer must include and avoid

## Strongly recommended fields

- `expected_sources` (array of strings): URLs, page titles, or document IDs expected to support the answer
- `notes` (string): optional clarifications for reviewers
- `tags` (array of strings): scenario tags such as `pricing`, `about-page`, `policy`, `multi-hop`

## Rules

1. Keep question text realistic (copied or adapted from real users).
2. Write `expected_answer` for factual cases in a deterministic way.
3. Write `judge_rubric` for open-ended cases as clear pass/fail criteria.
4. Include source references whenever the question is site/business specific.
5. Keep one concern per testcase (do not combine multiple unrelated questions).
6. Use English first for v1 unless multilingual behavior is in scope now.

## Example rubric style

- Must mention product/service purpose in plain language.
- Must avoid invented pricing details.
- Must acknowledge missing information when retrieval context is incomplete.
- Should recommend contacting business only when key details are unavailable.
