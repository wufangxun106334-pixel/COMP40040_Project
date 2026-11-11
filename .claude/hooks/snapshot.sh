#!/bin/bash
# 改动前快照 manufacturing-mayhem/src/main；内容与上次快照相同则跳过，只保留最近 50 份
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/manufacturing-mayhem/src/main"
DEST="$ROOT/docs/backup/auto"
[ -d "$SRC" ] || exit 0
mkdir -p "$DEST"

HASH=$(cd "$SRC" && find . -type f \( -name '*.astra' -o -name '*.java' \) -print0 | sort -z | xargs -0 shasum | shasum | cut -d' ' -f1)
[ "$HASH" = "$(cat "$DEST/.last" 2>/dev/null)" ] && exit 0

SNAP="$DEST/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$SNAP"
(cd "$SRC" && find . -type f \( -name '*.astra' -o -name '*.java' \) -print0 | xargs -0 -I{} rsync -R {} "$SNAP/")
echo "$HASH" > "$DEST/.last"

ls -1d "$DEST"/2* 2>/dev/null | sort | awk -v n="$(ls -1d "$DEST"/2* 2>/dev/null | wc -l)" 'NR <= n - 50' | xargs rm -rf
exit 0
