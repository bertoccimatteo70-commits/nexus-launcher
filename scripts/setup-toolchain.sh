#!/usr/bin/env bash
# Local development toolchain bootstrap for Nexus Launcher.
# Installs JDK 17, Android SDK 35 and Gradle 8.10.2 under $HOME (never inside the repo).
# CI uses the GitHub Actions runner's toolchain instead; see .github/workflows/android.yml.
set -euo pipefail

ROOT="${TOOLCHAIN_DIR:-$HOME/toolchain}"
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
GRADLE_VERSION="8.10.2"
CMDLINE_TOOLS="commandlinetools-linux-11076708_latest.zip"
JDK_URL="${JDK_URL:-https://corretto.aws/downloads/latest/amazon-corretto-17-x64-linux-jdk.tar.gz}"
GRADLE_URL="${GRADLE_URL:-https://downloads.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip}"

mkdir -p "$ROOT" "$SDK"

if [ ! -x "$ROOT/jdk17/bin/java" ]; then
  echo "[setup] downloading JDK 17"
  curl -fsSL -o "$ROOT/jdk17.tar.gz" "$JDK_URL"
  mkdir -p "$ROOT/jdk17"
  tar -xzf "$ROOT/jdk17.tar.gz" -C "$ROOT/jdk17" --strip-components=1
  rm -f "$ROOT/jdk17.tar.gz"
fi

export JAVA_HOME="$ROOT/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "[setup] downloading Android command line tools"
  curl -fsSL -o "$ROOT/cmdline-tools.zip" "https://dl.google.com/android/repository/$CMDLINE_TOOLS"
  mkdir -p "$SDK/cmdline-tools"
  unzip -q -o "$ROOT/cmdline-tools.zip" -d "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -f "$ROOT/cmdline-tools.zip"
fi

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"

echo "[setup] accepting Android SDK licenses"
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses > /dev/null || true

echo "[setup] installing platform 35 and build tools"
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"

if [ ! -x "$ROOT/gradle-$GRADLE_VERSION/bin/gradle" ]; then
  echo "[setup] downloading Gradle $GRADLE_VERSION"
  curl -fsSL -o "$ROOT/gradle.zip" "$GRADLE_URL"
  unzip -q -o "$ROOT/gradle.zip" -d "$ROOT"
  rm -f "$ROOT/gradle.zip"
fi

echo "[setup] toolchain ready"
echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
"$ROOT/gradle-$GRADLE_VERSION/bin/gradle" --version
