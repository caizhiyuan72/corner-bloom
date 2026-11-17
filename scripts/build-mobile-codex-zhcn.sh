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
# Android WebView serves only explicitly allowed packaged files.
# Register the added Chinese catalog without weakening the origin/path allowlist.
python3 - <<'PY'
from pathlib import Path
file=Path('app/src/main/java/dev/mobilecodex/app/MainActivity.java')
source=file.read_text(encoding='utf-8')
old='path.equals("/translations.js") || path.equals("/locale.js")'
new='path.equals("/translations.js") || path.equals("/translations-zh.js") || path.equals("/locale.js")'
assert source.count(old)==1, 'Unexpected WebView routing source'
file.write_text(source.replace(old,new),encoding='utf-8')
PY
python3 "$ROOT/mobile-codex-zhcn/apply_api_providers.py" "$WORK"
python3 "$ROOT/mobile-codex-zhcn/apply_zh_v2.py" "$WORK"
# Guard API-only authentication behavior against regressions.
python3 - <<'PY'
from pathlib import Path
engine=Path('app/src/main/java/dev/mobilecodex/app/Engine.java').read_text()
ui=Path('app/src/main/assets/web/app.js').read_text()
store=Path('app/src/main/java/dev/mobilecodex/app/ApiProviderStore.java').read_text()
assert 'if (!accountProfiles.hasSavedLogin()) authState = apiProviders.isActive() ? "checking" : "signed_out";' in engine
assert 'if (!ready && !accountProfiles.hasSavedLogin() && !apiProviders.isActive())' in engine
assert 'account = obj("type", "api_key"); authState = "signed_in";' in engine
assert 'if (apiProviders.isActive()) models = apiProviders.models()' in engine
assert 'function configuredApi()' in ui
assert "if (!add && configuredApi()) { await call('runtime.start'); return; }" in ui
assert "await call('providers.select',{id:chosen});" in Path('app/src/main/assets/web/api-providers.js').read_text()
assert 'builder.command().addAll(1, args);' in store
print('Verified provider login bypass, automatic selection and runtime config paths')
PY
# Distinct Android package ID: debug signing keys from different CI runs cannot update one another.
# Keep the original Chinese-only and English apps installed with their local data intact.
python3 - <<'PY'
from pathlib import Path
gradle=Path('app/build.gradle')
source=gradle.read_text()
old="applicationIdSuffix '.zhcn'"
new="applicationIdSuffix '.zhcnv2'"
assert source.count(old)==1, 'Unexpected localized debug package ID'
gradle.write_text(source.replace(old,new))
manifest=Path('app/src/main/AndroidManifest.xml')
source=manifest.read_text()
old='android:label="Mobile Codex 中文版"'
new='android:label="Mobile Codex 中文增强版"'
assert source.count(old)==1, 'Unexpected localized app label'
manifest.write_text(source.replace(old,new))
PY
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
cp app/build/outputs/apk/debug/app-debug.apk "$ROOT/apk-output/mobile-codex-v0.3.9-zhCN-v2-arm64-debug.apk"
sha256sum "$ROOT/apk-output/"*.apk > "$ROOT/apk-output/SHA256SUMS.txt"
echo "Build succeeded. APK is in apk-output/"
