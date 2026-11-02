#!/bin/bash
# Ставит Android SDK в облачной сессии Claude Code (в т.ч. запущенной с телефона).
# Локально ничего не делает. Повторные запуски пропускают установку.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}"

SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
# Версия cmdline-tools: https://developer.android.com/studio#command-line-tools-only
CMDLINE_TOOLS_ZIP="commandlinetools-linux-16111833_latest.zip"
# Должно совпадать с compileSdk в app/build.gradle.kts и build tools AGP
PACKAGES=("platform-tools" "platforms/android-37.0" "build-tools/36.0.0")

ANDROID="$SDK/cmdline-tools/latest/bin/android"
hashes="$SDK/.session-start.sha256"

if ! sha256sum --check --status "$hashes" 2>/dev/null; then
  rm -f "$hashes"
  if [ ! -x "$ANDROID" ]; then
    tmp="$(mktemp -d)"
    curl -fsSL --retry 3 -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
    unzip -q "$tmp/tools.zip" -d "$tmp"
    mkdir -p "$SDK/cmdline-tools"
    rm -rf "$SDK/cmdline-tools/latest"
    mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
    rm -rf "$tmp"
  fi
  "$ANDROID" --no-metrics --sdk="$SDK" sdk install "${PACKAGES[@]}" 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS' >&2
  # Skill Android CLI для агентов (не критично, если не получится)
  "$ANDROID" --no-metrics --sdk="$SDK" init >/dev/null 2>&1 || true
  sha256sum .claude/hooks/session-start.sh > "$hashes"
fi

# Каждой сессии: путь к SDK для Gradle и для shell
echo "sdk.dir=$SDK" > local.properties
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export ANDROID_HOME=\"$SDK\""
    echo "export ANDROID_SDK_ROOT=\"$SDK\""
    echo "export PATH=\"$SDK/platform-tools:$SDK/cmdline-tools/latest/bin:\$PATH\""
  } >> "$CLAUDE_ENV_FILE"
fi
