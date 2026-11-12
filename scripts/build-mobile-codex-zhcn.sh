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
python3 -m pip install -r tools/requirements-devtools.txt
python3 tools/prepare_runtime.py
python3 tools/build_native.py --ndk "$ANDROID_HOME/ndk/28.2.13676358"
# Official Termux pool rotated an older pinned package; use an archive mirror.
# Retain all upstream SHA-256 hashes and expected package filenames unchanged.
python3 - <<'PY'
from pathlib import Path
import json
path=Path('tools/devtools-lock.json')
lock=json.loads(path.read_text())
assert lock['poolUrl']=='https://packages.termux.dev/apt/termux-main/'
lock['poolUrl']='https://repository.su/termux/termux-main/'
path.write_text(json.dumps(lock,ensure_ascii=False,indent=2))
PY
python3 tools/prepare_devtools.py
python3 tools/prepare_devtools.py --verify
npm ci --ignore-scripts
npm test
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug
test -s app/build/outputs/apk/debug/app-debug.apk
mkdir -p "$ROOT/apk-output"
cp app/build/outputs/apk/debug/app-debug.apk "$ROOT/apk-output/mobile-codex-v0.3.9-zhCN-arm64-debug.apk"
sha256sum "$ROOT/apk-output/"*.apk > "$ROOT/apk-output/SHA256SUMS.txt"
echo "Build succeeded. APK is in apk-output/"
