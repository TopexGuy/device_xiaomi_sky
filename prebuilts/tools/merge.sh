#!/bin/bash

set -e

TARGET_DIR="${1:-.}"

if [ ! -d "$TARGET_DIR" ]; then
    echo "[!] Directory not found: $TARGET_DIR"
    exit 1
fi

echo "[*] Target: $TARGET_DIR"
echo

merged=0
skipped=0

while IFS= read -r -d '' part0; do
    base="${part0%.part000}"

    echo "[*] Processing: $base"

    if [ ! -f "${base}.hash" ]; then
        echo "[!] Missing hash: $base"
        skipped=$((skipped+1))
        continue
    fi

    (
        cd "$(dirname "$base")"
        sha256sum -c "$(basename "$base").hash"
    ) > /dev/null 2>&1

    if [ $? -ne 0 ]; then
        echo "[✗] Corrupt/missing parts: $base"
        skipped=$((skipped+1))
        continue
    fi

    if [ -f "$base" ]; then
        echo "[!] File already exists: $base"
        skipped=$((skipped+1))
        continue
    fi

    echo "[+] Merging: $base"

    cat "${base}.part"* > "$base"

    rm -f "${base}.part"* "${base}.hash"

    merged=$((merged+1))

done < <(find "$TARGET_DIR" -type f -name "*.part000" -print0)

echo
echo "[✓] Done ($merged merged, $skipped skipped)"
