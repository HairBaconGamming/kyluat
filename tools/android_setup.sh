#!/usr/bin/env bash
# Tải JDK 17, Android command-line tools, Gradle và cài SDK packages để build APK.
set -euo pipefail

ROOT="$HOME/AndroidToolchain"
DL="$ROOT/downloads"
mkdir -p "$DL" "$ROOT/jdk" "$ROOT/sdk" "$ROOT/gradle"

fetch() {
  local url="$1" out="$2"
  if [ -s "$out" ]; then
    echo "skip download $out"
    return
  fi
  echo "download $url"
  curl -L --fail --retry 3 --retry-delay 2 -o "$out" "$url"
}

fetch "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" "$DL/jdk17.zip"
fetch "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" "$DL/cmdtools.zip"
fetch "https://services.gradle.org/distributions/gradle-8.7-bin.zip" "$DL/gradle.zip"

echo "extract jdk"
unzip -q -o "$DL/jdk17.zip" -d "$ROOT/jdk"
echo "extract cmdline-tools"
rm -rf "$ROOT/cmdtools"
unzip -q -o "$DL/cmdtools.zip" -d "$ROOT/cmdtools"
mkdir -p "$ROOT/sdk/cmdline-tools"
rm -rf "$ROOT/sdk/cmdline-tools/latest"
mv "$ROOT/cmdtools/cmdline-tools" "$ROOT/sdk/cmdline-tools/latest"
echo "extract gradle"
unzip -q -o "$DL/gradle.zip" -d "$ROOT/gradle"

JDK_HOME="$(ls -d "$ROOT"/jdk/jdk-17* | head -1)"
export JAVA_HOME="$(cygpath -w "$JDK_HOME")"
export PATH="$JDK_HOME/bin:$PATH"
ROOT_WIN="$(cygpath -w "$ROOT")"
SDKMANAGER="$(cygpath -w "$ROOT/sdk/cmdline-tools/latest/bin/sdkmanager.bat")"

echo "java:"
java -version 2>&1 | head -2

echo "accept licenses"
yes | cmd //c "$SDKMANAGER --sdk_root=$ROOT_WIN --licenses" >/dev/null 2>&1 || true

echo "install packages"
cmd //c "$SDKMANAGER --sdk_root=$ROOT_WIN platform-tools platforms;android-34 build-tools;34.0.0"

echo "SETUP_DONE"
echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ROOT_WIN"
echo "GRADLE=$(cygpath -w "$ROOT/gradle/gradle-8.7/bin/gradle.bat")"
