#!/usr/bin/env python3
"""
Split a large bible_embeddings.json into smaller part files for import on Render.
Use when the full file is too large to download from Google Drive in one go.

Each part file has the same format as the original: { "verses": [ ... ] }.
Upload each part to Google Drive (or similar), then set on Render:
  IMPORT_EMBEDDINGS_URLS=https://url-to-part1,https://url-to-part2,...

Usage:
  python scripts/split-embeddings-for-import.py data/bible_embeddings.json [--max-mb 80] [--out-dir data/parts]

Requires enough RAM to load the full JSON (e.g. ~2x file size). For a 600MB file, have 2GB+ free.
"""

import argparse
import json
import os
import sys


def main():
    parser = argparse.ArgumentParser(description="Split bible_embeddings.json into smaller parts for import.")
    parser.add_argument("input_file", help="Path to bible_embeddings.json")
    parser.add_argument("--max-mb", type=float, default=80,
                        help="Max size per part in MB (default 80, safe for Google Drive)")
    parser.add_argument("--out-dir", default="data/embedding_parts",
                        help="Output directory for part files (default: data/embedding_parts)")
    args = parser.parse_args()

    input_path = args.input_file
    max_bytes = int(args.max_mb * 1024 * 1024)
    out_dir = args.out_dir

    if not os.path.isfile(input_path):
        print(f"Error: File not found: {input_path}", file=sys.stderr)
        sys.exit(1)
    os.makedirs(out_dir, exist_ok=True)

    print(f"Loading {input_path} (this may use significant RAM)...")
    with open(input_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    verses = data.get("verses")
    if not verses:
        print("Error: JSON must have a 'verses' array.", file=sys.stderr)
        sys.exit(1)
    print(f"Total verses: {len(verses)}")

    part_index = 0
    current_chunk = []
    current_size = 0
    part_paths = []

    for v in verses:
        # Approximate size of this verse in JSON (one item + comma)
        item_size = len(json.dumps(v)) + 1
        if current_chunk and (current_size + item_size) > max_bytes:
            part_index += 1
            out_path = os.path.join(out_dir, f"bible_embeddings_part_{part_index}.json")
            with open(out_path, "w", encoding="utf-8") as out:
                json.dump({"verses": current_chunk}, out, ensure_ascii=False)
            part_paths.append(out_path)
            print(f"  Part {part_index}: {len(current_chunk)} verses -> {out_path}")
            current_chunk = []
            current_size = 0
        current_chunk.append(v)
        current_size += item_size

    if current_chunk:
        part_index += 1
        out_path = os.path.join(out_dir, f"bible_embeddings_part_{part_index}.json")
        with open(out_path, "w", encoding="utf-8") as out:
            json.dump({"verses": current_chunk}, out, ensure_ascii=False)
        part_paths.append(out_path)
        print(f"  Part {part_index}: {len(current_chunk)} verses -> {out_path}")

    print(f"\nDone. {len(part_paths)} part file(s) in {out_dir}/")
    print("Next steps:")
    print("  1. Upload each part to Google Drive (or similar); get a direct download URL for each.")
    print("  2. On Render, set IMPORT_EMBEDDINGS_URLS to a comma-separated list of those URLs.")
    print("  3. After import succeeds, remove IMPORT_EMBEDDINGS_URLS.")


if __name__ == "__main__":
    main()
