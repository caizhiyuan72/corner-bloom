#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/mobile-codex-upstream"
git clone --depth 1 --branch v0.3.9 https://github.com/nokryong/mobile-codex.git "$WORK"
EXPECTED=b6c12eb65fd29a38b0c2825bf12a7aca64736c5a
ACTUAL="$(git -C "$WORK" rev-parse HEAD)"
if [ "$ACTUAL" != "$EXPECTED" ]; then echo "Unexpected upstream commit $ACTUAL"; exit 1; fi
python3 "$ROOT/mobile-codex-zhcn/apply_zhcn.py" "$WORK" --parallel
cd "$WORK"
# Reuse signed upstream APK runtime assets to avoid retired historical Termux packages.
# Verify GitHub release artifact against the published SHA-256 before extraction.
APK="$RUNNER_TEMP/mobile-codex-upstream-v0.3.9.apk"
curl --fail --location --retry 3 --retry-delay 4 \
  "https://github.com/nokryong/mobile-codex/releases/download/v0.3.9/mobile-codex-0.3.9-arm64.apk" \
  --output "$APK"
echo "48f05bd8812a5907a4e80bf8d228afc754875f497c0c380e94a06e1db2ec6731  $APK" | sha256sum -c -
EXTRACT="$RUNNER_TEMP/mobile-codex-release-assets"
mkdir -p "$EXTRACT"
unzip -q "$APK" "assets/runtime/*" "assets/devtools/*" "lib/arm64-v8a/*" -d "$EXTRACT"
mkdir -p app/src/main/assets/runtime app/src/main/assets/devtools app/src/main/jniLibs/arm64-v8a
cp -a "$EXTRACT/assets/runtime/." app/src/main/assets/runtime/
cp -a "$EXTRACT/assets/devtools/." app/src/main/assets/devtools/
cp -a "$EXTRACT/lib/arm64-v8a/." app/src/main/jniLibs/arm64-v8a/
test -s app/src/main/assets/devtools/payload.zip
test -s app/src/main/assets/devtools/manifest.json
test -s app/src/main/jniLibs/arm64-v8a/libcodex.so
npm ci --ignore-scripts
npm test
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug
test -s app/build/outputs/apk/debug/app-debug.apk
mkdir -p "$ROOT/apk-output"
cp app/build/outputs/apk/debug/app-debug.apk "$ROOT/apk-output/mobile-codex-v0.3.9-zhCN-arm64-debug.apk"
sha256sum "$ROOT/apk-output/"*.apk > "$ROOT/apk-output/SHA256SUMS.txt"
echo "Build succeeded. APK is in apk-output/"
