# Dataset v1 TODO (Step 2)

Use this checklist to complete your first production-grade eval dataset.

## Build the first 50 cases

- [ ] Add 20 factual cases (site/business-specific)
- [ ] Add 20 open-ended cases (recommendations, guidance, nuanced answers)
- [ ] Add 10 safety/policy cases (prompt injection, abuse, disallowed behavior)

## Quality checks

- [ ] Every testcase has unique `id`
- [ ] Every testcase has `case_type` and `risk`
- [ ] Every factual testcase has deterministic `expected_answer`
- [ ] Every open-ended testcase has a clear `judge_rubric`
- [ ] Site-specific testcases include `expected_sources`
- [ ] No testcase combines two unrelated questions

## Failure-mode targeting

- [ ] At least 10 cases for retrieval misses (context not found)
- [ ] At least 10 cases for grounding errors (answer not supported)
- [ ] At least 10 cases for ranking issues (right chunk exists but low rank)
- [ ] At least 5 cases with long context/noise
- [ ] At least 5 cases with lexical mismatch (synonyms, alternate wording)

## Review and freeze

- [ ] Manually review all `critical` cases with domain owner
- [ ] Remove ambiguous expected answers
- [ ] Save as `evals/datasets/dataset_v1.jsonl`
- [ ] Tag file version in notes (`dataset_v1.0`)
