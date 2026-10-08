#!/usr/bin/env bash
# Builds a signed release APK locally and publishes it as a GitHub release.
# Usage: scripts/release-local.sh v0.1.0 [path/to/keystore.jks]
set -euo pipefail

TAG="${1:?Usage: $0 <tag, e.g. v0.1.0> [keystore]}"
KEYSTORE="${2:-$HOME/androcleaner-release.jks}"
[[ -f "$KEYSTORE" ]] || { echo "Keystore not found: $KEYSTORE" >&2; exit 1; }

if [[ -z "${JAVA_HOME:-}" && -d /opt/homebrew/opt/openjdk@21 ]]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi

read -rsp "Keystore password: " PASSWORD
echo

export ANDROCLEANER_KEYSTORE="$KEYSTORE"
export ANDROCLEANER_KEYSTORE_PASSWORD="$PASSWORD"
export ANDROCLEANER_KEY_ALIAS="${KEY_ALIAS:-androcleaner}"
export ANDROCLEANER_KEY_PASSWORD="$PASSWORD"

cd "$(dirname "$0")/.."

# F-Droid rebuilds the tagged commit and compares it with our APK, so build exactly that commit.
TAG_COMMIT="$(git rev-parse "$TAG^{commit}" 2>/dev/null)" || { echo "Tag $TAG not found. Create and push it first." >&2; exit 1; }
if [[ "$(git rev-parse HEAD)" != "$TAG_COMMIT" || -n "$(git status --porcelain -- app gradle build.gradle.kts settings.gradle.kts gradle.properties)" ]]; then
    echo "HEAD is not at $TAG or there are uncommitted build changes. Run: git checkout $TAG" >&2
    exit 1
fi
./gradlew testStandardDebugUnitTest assembleStandardRelease assembleFullRelease

mkdir -p build
APKS=("build/Androcleaner-$TAG.apk" "build/Androcleaner-Full-$TAG.apk")
cp app/build/outputs/apk/standard/release/app-standard-release.apk "${APKS[0]}"
cp app/build/outputs/apk/full/release/app-full-release.apk "${APKS[1]}"
echo "Built ${APKS[*]}"

if gh release view "$TAG" >/dev/null 2>&1; then
    gh release upload "$TAG" "${APKS[@]}" --clobber
else
    gh release create "$TAG" "${APKS[@]}" --title "Androcleaner $TAG" --notes-file scripts/release-notes.md --generate-notes
fi
