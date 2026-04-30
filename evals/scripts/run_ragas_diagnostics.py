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
from ragas.embeddings import LangchainEmbeddingsWrapper
from ragas.llms import LangchainLLMWrapper
from ragas.metrics import (
    answer_relevancy,
    context_precision,
    context_recall,
    faithfulness,
)
from langchain_anthropic import ChatAnthropic
from langchain_huggingface import HuggingFaceEmbeddings


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


def _safe_mean(df: pd.DataFrame, column: str) -> float | None:
    if column not in df.columns:
        return None
    series = pd.to_numeric(df[column], errors="coerce").dropna()
    if series.empty:
        return None
    return float(series.mean())


def run() -> int:
    anthropic_key = os.getenv("ANTHROPIC_API_KEY", "").strip()
    if not anthropic_key:
        raise EnvironmentError(
            "Missing ANTHROPIC_API_KEY. This Step 4 runner is configured for Anthropic-only judging."
        )
    model_name = os.getenv("ANTHROPIC_MODEL", "claude-haiku-4-5-20251001").strip()
    if model_name == "claude-3-5-haiku-latest":
        raise EnvironmentError(
            "ANTHROPIC_MODEL is set to deprecated/unavailable value 'claude-3-5-haiku-latest'. "
            "Unset it or set ANTHROPIC_MODEL=claude-haiku-4-5-20251001."
        )

    rows = _normalize_rows(_parse_jsonl(INPUT_PATH))
    dataset = Dataset.from_list(rows)
    llm = LangchainLLMWrapper(
        ChatAnthropic(
            model=model_name,
            temperature=0,
            max_tokens=512,
            anthropic_api_key=anthropic_key,
        )
    )
    embeddings = LangchainEmbeddingsWrapper(
        HuggingFaceEmbeddings(
            model_name=os.getenv("RAGAS_EMBEDDING_MODEL", "sentence-transformers/all-MiniLM-L6-v2")
        )
    )

    result = evaluate(
        dataset,
        metrics=[faithfulness, answer_relevancy, context_precision, context_recall],
        llm=llm,
        embeddings=embeddings,
    )

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)

    df = result.to_pandas()
    df.to_csv(CSV_PATH, index=False)

    metrics = {
        "model": model_name,
        "cases_evaluated": len(df),
        "faithfulness": _safe_mean(df, "faithfulness"),
        "answer_relevancy": _safe_mean(df, "answer_relevancy"),
        "context_precision": _safe_mean(df, "context_precision"),
        "context_recall": _safe_mean(df, "context_recall"),
    }
    JSON_PATH.write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    MD_PATH.write_text(_build_summary(metrics, row_count=len(df)), encoding="utf-8")

    print(f"Saved: {CSV_PATH}")
    print(f"Saved: {JSON_PATH}")
    print(f"Saved: {MD_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(run())
