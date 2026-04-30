#!/usr/bin/env python3
"""Build a simple markdown summary from pytest JUnit XML output."""

from __future__ import annotations

import html
import os
import xml.etree.ElementTree as ET
from pathlib import Path


RESULTS_DIR = Path("evals/results")
JUNIT_PATH = RESULTS_DIR / "junit.xml"
SUMMARY_PATH = RESULTS_DIR / "summary.md"


def _safe_text(value: str | None) -> str:
    if not value:
        return ""
    return html.unescape(value).strip()


def build_summary() -> int:
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)

    if not JUNIT_PATH.exists():
        SUMMARY_PATH.write_text(
            "# RAG Eval Summary\n\n"
            "No `junit.xml` file was found. This usually means tests did not run (for example, missing secrets).\n",
            encoding="utf-8",
        )
        return 0

    tree = ET.parse(JUNIT_PATH)
    root = tree.getroot()

    suite = root.find("testsuite")
    if suite is None and root.tag == "testsuite":
        suite = root

    if suite is None:
        SUMMARY_PATH.write_text(
            "# RAG Eval Summary\n\nUnable to parse JUnit structure.\n",
            encoding="utf-8",
        )
        return 0

    tests = int(suite.attrib.get("tests", "0"))
    failures = int(suite.attrib.get("failures", "0"))
    errors = int(suite.attrib.get("errors", "0"))
    skipped = int(suite.attrib.get("skipped", "0"))
    passed = max(0, tests - failures - errors - skipped)
    duration_s = suite.attrib.get("time", "0")

    lines = [
        "# RAG Eval Summary",
        "",
        "## Overview",
        f"- Total tests: **{tests}**",
        f"- Passed: **{passed}**",
        f"- Failed: **{failures}**",
        f"- Errors: **{errors}**",
        f"- Skipped: **{skipped}**",
        f"- Duration (s): **{duration_s}**",
        "",
    ]

    failed_cases = []
    for case in suite.findall("testcase"):
        case_name = _safe_text(case.attrib.get("name")) or "unnamed_case"
        failure = case.find("failure")
        error = case.find("error")
        if failure is None and error is None:
            continue
        node = failure if failure is not None else error
        msg = _safe_text(node.attrib.get("message")) or _safe_text(node.text)
        failed_cases.append((case_name, msg[:300]))

    if failed_cases:
        lines.extend(["## Failed cases", ""])
        for name, msg in failed_cases:
            lines.append(f"- `{name}`: {msg or 'No message provided.'}")
        lines.append("")
    else:
        lines.extend(["## Failed cases", "", "- None 🎉", ""])

    lines.extend(
        [
            "## Where to inspect details",
            "",
            "- GitHub Actions log: open workflow run -> `DeepEval Smoke` job",
            "- Artifact: download `rag-eval-results` and open `junit.xml`",
        ]
    )

    SUMMARY_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(build_summary())
