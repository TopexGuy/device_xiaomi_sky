#!/usr/bin/env bash

REMOVE_PATHS=(
  "vendor/qcom/opensource/vibrator"
  "vendor/lineage-priv"
  "vendor/bcr"
  "hardware/xiaomi/dolby"
)

REPOS=(
  "https://github.com/topexguy/kernel_xiaomi_sky.git|17|kernel/xiaomi/sky|-d"
  "https://github.com/topexguy/kernel_xiaomi_sm8450-modules.git|17|kernel/xiaomi/sm8450-modules|-d"
  "https://github.com/topexguy/vendor_xiaomi_sky.git|17|vendor/xiaomi/sky|-d"
  "https://github.com/anonytry/android_vendor_qcom_opensource_vibrator.git|.|vendor/qcom/opensource/vibrator|-d"
  "https://github.com/anonytry/android_vendor_bcr.git|.|vendor/bcr|-d"
  "https://github.com/anonytry/android_hardware_xiaomi.git|.|hardware/xiaomi|-d"
  "https://github.com/anonytry/hardware_dolby.git|17|hardware/dolby|-d"
#  "https://github.com/topexguy/vendor_xiaomi_miuicamera-sky|.|vendor/xiaomi/miuicamera-sky|-d"
#  "https://github.com/topexguy/device_xiaomi_miuicamera-sky|.|device/xiaomi/miuicamera-sky|-d"
)

FAILED_CLONES=()
SKIPPED_CLONES=()
SUCCESS_CLONES=()

echo
echo "==> Removing selected paths..."

for path in "${REMOVE_PATHS[@]}"; do
  if [[ -e "$path" ]]; then
    rm -rf "$path"
    echo "  Removed: $path"
  else
    echo "  Not found: $path"
  fi
done

echo
echo "==> Cloning repositories..."

for repo in "${REPOS[@]}"; do
  IFS='|' read -r url branch path option <<< "$repo"

  if [[ -e "$path" ]]; then
    echo "  SKIP: $path already exists"
    SKIPPED_CLONES+=("$url -> $path")
    continue
  fi

  mkdir -p "$(dirname "$path")"

  clone_opts=()

  case "$option" in
    -d|--depth)
      clone_opts+=(--depth=1)
      ;;
    "")
      ;;
    *)
      echo "  INVALID OPTION: $option"
      FAILED_CLONES+=("$url -> $path [invalid option: $option]")
      continue
      ;;
  esac

  echo "  Clone: $url -> $path"

  if [[ "$branch" == "." ]]; then
    if git clone "${clone_opts[@]}" "$url" "$path"; then
      SUCCESS_CLONES+=("$url -> $path")
    else
      FAILED_CLONES+=("$url -> $path")
      rm -rf "$path"
    fi
  else
    if git clone "${clone_opts[@]}" -b "$branch" "$url" "$path"; then
      SUCCESS_CLONES+=("$url [$branch] -> $path")
    else
      FAILED_CLONES+=("$url [$branch] -> $path")
      rm -rf "$path"
    fi
  fi
done

echo
echo "==> Running Signify..."

KEYS_DIR="vendor/signify/keys" \
SKIP_OTA=true \
bash <(curl -s https://raw.githubusercontent.com/TopexGuy/Signify/main/signify.sh) --auto

echo
echo "========================================"
echo "             CLONE SUMMARY"
echo "========================================"

echo
echo "✓ Successful: ${#SUCCESS_CLONES[@]}"
for item in "${SUCCESS_CLONES[@]}"; do
  echo "  $item"
done

echo
echo "- Skipped: ${#SKIPPED_CLONES[@]}"
for item in "${SKIPPED_CLONES[@]}"; do
  echo "  $item"
done

echo
echo "✗ Failed: ${#FAILED_CLONES[@]}"
for item in "${FAILED_CLONES[@]}"; do
  echo "  $item"
done

echo
echo "========================================"
