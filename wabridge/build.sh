#!/usr/bin/env bash
# يبني app/libs/wabridge.aar (whatsmeow عبر gomobile). يُستدعى من Gradle تلقائياً إن لم يوجد الملف.
set -euo pipefail
cd "$(dirname "$0")"
echo "[wabridge] start"
command -v go >/dev/null || { echo "[wabridge] ERROR: go غير مثبّت على جهاز البناء"; exit 1; }
go version

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -d "${ANDROID_NDK_HOME:-/nonexistent}" ]; then
  if [ -n "$SDK" ] && [ -d "$SDK/ndk" ] && [ -n "$(ls "$SDK/ndk" 2>/dev/null)" ]; then
    ANDROID_NDK_HOME="$SDK/ndk/$(ls "$SDK/ndk" | sort -V | tail -1)"
  else
    SM="$(command -v sdkmanager || echo "$SDK/cmdline-tools/latest/bin/sdkmanager")"
    yes | "$SM" --licenses >/dev/null 2>&1 || true
    "$SM" "ndk;27.0.12077973"
    ANDROID_NDK_HOME="$SDK/ndk/27.0.12077973"
  fi
fi
export ANDROID_NDK_HOME
echo "[wabridge] NDK: $ANDROID_NDK_HOME"

export PATH="$PATH:$(go env GOPATH)/bin"
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init
go get go.mau.fi/whatsmeow@latest github.com/mattn/go-sqlite3 golang.org/x/mobile/bind google.golang.org/protobuf
go mod tidy
mkdir -p ../app/libs
gomobile bind -v -target=android/arm64,android/arm -androidapi 26 -o ../app/libs/wabridge.aar .
echo "[wabridge] OK"
