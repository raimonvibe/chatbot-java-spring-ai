#!/usr/bin/env bash
# Convert a Google Drive sharing link to a direct download URL for wget/curl.
#
# Usage:
#   ./scripts/google-drive-download-url.sh "https://drive.google.com/file/d/FILE_ID/view?usp=sharing"
#   ./scripts/google-drive-download-url.sh FILE_ID
#
# Then use the printed URL with wget:
#   wget -O bible_embeddings.json "URL_FROM_SCRIPT"

set -e

INPUT="${1:-}"
if [ -z "$INPUT" ]; then
  echo "Usage: $0 <google-drive-url-or-file-id>"
  echo ""
  echo "Examples:"
  echo "  $0 'https://drive.google.com/file/d/1NA-n65-sW-bCWZiAjunmEVzQMlbnf16d/view?usp=sharing'"
  echo "  $0 1NA-n65-sW-bCWZiAjunmEVzQMlbnf16d"
  exit 1
fi

# Extract file ID: from URL (d/ID/view) or use as-is if it looks like an ID (alphanumeric, dashes)
if [[ "$INPUT" == *"drive.google.com"* ]]; then
  FILE_ID=$(echo "$INPUT" | sed -n 's|.*/d/\([a-zA-Z0-9_-]*\)/.*|\1|p')
  if [ -z "$FILE_ID" ]; then
    echo "Could not parse File ID from URL: $INPUT"
    exit 1
  fi
else
  FILE_ID="$INPUT"
fi

echo "File ID: $FILE_ID"
echo ""
echo "Use one of these URLs for wget/curl (direct download):"
echo ""
echo "  https://drive.google.com/uc?export=download&id=$FILE_ID"
echo ""
echo "Or for large files (avoids virus scan page):"
echo ""
echo "  https://drive.usercontent.google.com/download?id=$FILE_ID&export=download&confirm=t"
echo ""
echo "Example wget:"
echo "  wget -O bible_embeddings.json \"https://drive.usercontent.google.com/download?id=$FILE_ID&export=download&confirm=t\""
echo ""
