#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/frontend/user-extension"
OUT="${1:-$ROOT/dist}"
VERSION="$(node -e "console.log(require('$SRC/manifest.json').version)")"
mkdir -p "$OUT"
ZIP="$OUT/shiye-extension-$VERSION.zip"
rm -f "$ZIP"
cd "$SRC"
zip -r "$ZIP" manifest.json src icons >/dev/null
echo "extension package created:"
echo "  $ZIP"
