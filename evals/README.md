# Evals Workspace (Step 2)

This folder contains the dataset scaffolding for Step 2 of the RAG evaluation plan.

## Goal

Create a high-quality dataset of 50-100 ground-truth question/answer cases focused on weak spots in your chatbot.

## Files

- `evals/datasets/dataset_v1_template.jsonl` - starter JSONL template with examples
- `evals/datasets/dataset_v1_schema.md` - field definitions and rules
- `evals/datasets/dataset_v1_todo.md` - practical checklist to complete dataset v1

## Quick start

1. Duplicate `dataset_v1_template.jsonl` into `dataset_v1.jsonl`.
2. Replace example rows with real domain questions from your users.
3. Keep all required fields present on every line.
4. Review each case against the schema and checklist.
5. Once you have at least 30 quality cases, proceed to Step 3 (CI checks).

## JSONL reminder

Each line must be one valid JSON object.
Do not wrap the file in `[` `]`.
