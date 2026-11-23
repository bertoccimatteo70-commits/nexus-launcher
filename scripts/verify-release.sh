#!/usr/bin/env bash
# Verifica l'APK Release di Nexus: firma, certificato, dimensione e SHA-256.
# Uso: ./scripts/verify-release.sh [percorso-apk]
set -euo pipefail

APK="${1:-app/build/outputs/apk/release/app-release.apk}"
if [[ ! -f "$APK" ]]; then
  echo "APK non trovato: $APK" >&2
  exit 1
fi

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
BUILD_TOOLS="$(find "$SDK/build-tools" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -n 1)"
if [[ -z "${BUILD_TOOLS:-}" ]]; then
  echo "build-tools Android non trovati in $SDK" >&2
  exit 1
fi

echo "APK: $APK"
echo "Dimensione: $(stat -c%s "$APK") byte"
echo

"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$APK"
echo
sha256sum "$APK"

SIZE=$(stat -c%s "$APK")
LIMIT=$((200 * 1024 * 1024))
if (( SIZE > LIMIT )); then
  echo "ERRORE: l'APK supera il limite di 200 MB" >&2
  exit 1
fi
echo "Limite 200 MB: rispettato"
