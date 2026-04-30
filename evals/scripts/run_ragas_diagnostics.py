#!/usr/bin/env python3
"""Run Step 4 Ragas diagnostics and export machine + human readable results."""

from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any

import pandas as pd
from datasets import Dataset
from ragas import evaluate
from ragas.metrics import (
    answer_relevancy,
    context_precision,
    context_recall,
    faithfulness,
)


ROOT = Path("evals")
INPUT_PATH = ROOT / "datasets" / "ragas_dataset_v1.jsonl"
RESULTS_DIR = ROOT / "results"
CSV_PATH = RESULTS_DIR / "ragas_metrics.csv"
JSON_PATH = RESULTS_DIR / "ragas_metrics.json"
MD_PATH = RESULTS_DIR / "ragas_summary.md"

REQUIRED_FIELDS = {"id", "user_input", "response", "retrieved_contexts", "reference"}
OPTIONAL_LIST_FIELDS = {"reference_contexts"}


def _parse_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        raise FileNotFoundError(
            f"Missing {path}. Copy evals/datasets/ragas_dataset_v1_template.jsonl to ragas_dataset_v1.jsonl first."
        )

    rows: list[dict[str, Any]] = []
    for idx, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        row = json.loads(line)
        missing = REQUIRED_FIELDS.difference(row.keys())
        if missing:
            raise ValueError(f"{path}:{idx} missing required fields: {sorted(missing)}")

        if not isinstance(row["retrieved_contexts"], list):
            raise ValueError(f"{path}:{idx} retrieved_contexts must be an array of strings")

        for key in OPTIONAL_LIST_FIELDS:
            if key in row and row[key] is not None and not isinstance(row[key], list):
                raise ValueError(f"{path}:{idx} {key} must be an array of strings when provided")

        rows.append(row)

    if not rows:
        raise ValueError(f"{path} is empty")
    return rows


def _normalize_rows(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    normalized: list[dict[str, Any]] = []
    for row in rows:
        normalized.append(
            {
                "id": str(row["id"]),
                "user_input": str(row["user_input"]),
                "response": str(row["response"]),
                "retrieved_contexts": [str(x) for x in row.get("retrieved_contexts", [])],
                "reference": str(row["reference"]),
                "reference_contexts": [str(x) for x in row.get("reference_contexts", [])],
            }
        )
    return normalized


def _build_summary(metrics: dict[str, Any], row_count: int) -> str:
    return (
        "# Ragas Diagnostics Summary\n\n"
        f"- Cases evaluated: **{row_count}**\n"
        f"- faithfulness: **{metrics.get('faithfulness', 0):.4f}**\n"
        f"- answer_relevancy: **{metrics.get('answer_relevancy', 0):.4f}**\n"
        f"- context_precision: **{metrics.get('context_precision', 0):.4f}**\n"
        f"- context_recall: **{metrics.get('context_recall', 0):.4f}**\n\n"
        "Interpretation tips:\n\n"
        "- Low context_recall usually means retriever misses needed chunks.\n"
        "- Low context_precision often means noisy retrieval.\n"
        "- Low faithfulness suggests answer claims not supported by retrieved contexts.\n"
    )


def run() -> int:
    if not (
        os.getenv("OPENAI_API_KEY", "").strip() or os.getenv("ANTHROPIC_API_KEY", "").strip()
    ):
        raise EnvironmentError(
            "Missing judge model credentials. Set OPENAI_API_KEY or ANTHROPIC_API_KEY before running Ragas."
        )

    rows = _normalize_rows(_parse_jsonl(INPUT_PATH))
    dataset = Dataset.from_list(rows)

    result = evaluate(
        dataset,
        metrics=[faithfulness, answer_relevancy, context_precision, context_recall],
    )

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)

    df = result.to_pandas()
    df.to_csv(CSV_PATH, index=False)

    metrics = result.to_dict()
    JSON_PATH.write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    MD_PATH.write_text(_build_summary(metrics, row_count=len(df)), encoding="utf-8")

    print(f"Saved: {CSV_PATH}")
    print(f"Saved: {JSON_PATH}")
    print(f"Saved: {MD_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(run())
