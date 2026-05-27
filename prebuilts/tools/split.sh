#!/bin/bash

set -e

TARGET_DIR="${1:-.}"
CHUNK_SIZE="${CHUNK_SIZE:-30M}"
THRESHOLD_MB="${THRESHOLD_MB:-45}"

if [ ! -d "$TARGET_DIR" ]; then
    echo "[!] Directory not found: $TARGET_DIR"
    exit 1
fi

echo "[*] Target: $TARGET_DIR"
echo "[*] Threshold: ${THRESHOLD_MB}MB"
echo "[*] Chunk size: $CHUNK_SIZE"
echo

split_count=0
skip_count=0

while IFS= read -r -d '' file; do
    filename="$(basename "$file")"

    case "$filename" in
        *.part*|*.hash)
            continue
            ;;
    esac

    size_mb=$(du -m "$file" | cut -f1)

    if [ "$size_mb" -lt "$THRESHOLD_MB" ]; then
        echo "[-] Skipped: $file (${size_mb}MB)"
        skip_count=$((skip_count+1))
        continue
    fi

    echo "[+] Splitting: $file (${size_mb}MB)"

    rm -f "${file}.part"* "${file}.hash"

    split -b "$CHUNK_SIZE" -d -a 3 "$file" "${file}.part"

    (
        cd "$(dirname "$file")"
        sha256sum "$(basename "$file").part"* \
            > "$(basename "$file").hash"
    )

    rm -f "$file"

    split_count=$((split_count+1))

done < <(find "$TARGET_DIR" -type f -print0)

echo
echo "[✓] Done ($split_count split, $skip_count skipped)"
