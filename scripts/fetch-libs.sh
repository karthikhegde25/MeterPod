#!/usr/bin/env bash
# Downloads the Mermaid renderer once so .mmd / .mermaid diagrams render offline in the File Viewer.
# (pdf.js is already bundled in the repo.) The app serves these files instead of the CDN, see FileViewerScreen.
set -euo pipefail
dir="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/fileviewer/libs"
mkdir -p "$dir"
curl -fsSL -o "$dir/mermaid.min.js" https://cdnjs.cloudflare.com/ajax/libs/mermaid/10.9.1/mermaid.min.js
echo "Bundled Mermaid saved to $dir"
