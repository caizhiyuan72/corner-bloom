#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
sdk_path="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
platform_jar="${BLOOM_ANDROID_JAR:-$sdk_path/platforms/android-35/android.jar}"
build_tools="${BLOOM_BUILD_TOOLS:-$sdk_path/build-tools/35.0.0}"
if [[ ! -f "$platform_jar" || ! -x "$build_tools/aapt2" ]]; then
  echo 'Install Android SDK platform 35 and build-tools 35.0.0, then set ANDROID_SDK_ROOT.' >&2
  exit 1
fi
build_dir="$project_dir/app/build/manual"
version_name="$(python3 -c 'import sys,xml.etree.ElementTree as E; print(E.parse(sys.argv[1]).getroot().attrib["{http://schemas.android.com/apk/res/android}versionName"])' "$project_dir/app/src/main/AndroidManifest.xml")"
mkdir -p "$build_dir/classes" "$build_dir/generated" "$build_dir/dex"
find "$build_dir/classes" -name '*.class' -delete
"$build_tools/aapt2" compile --dir "$project_dir/app/src/main/res" -o "$build_dir/resources.zip"
"$build_tools/aapt2" link -o "$build_dir/base.apk" -I "$platform_jar" \
  --manifest "$project_dir/app/src/main/AndroidManifest.xml" --java "$build_dir/generated" "$build_dir/resources.zip"
find "$project_dir/app/src/main/java" "$build_dir/generated" -name '*.java' > "$build_dir/sources.txt"
if command -v javac >/dev/null 2>&1; then
  javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$platform_jar:$build_tools/core-lambda-stubs.jar" -d "$build_dir/classes" @"$build_dir/sources.txt"
elif [[ -n "${BLOOM_ECJ_JAR:-}" ]]; then
  java -jar "$BLOOM_ECJ_JAR" -encoding UTF-8 -source 8 -target 8 -bootclasspath "$platform_jar:$build_tools/core-lambda-stubs.jar" -d "$build_dir/classes" @"$build_dir/sources.txt"
else
  echo 'A JDK with javac is required (or set BLOOM_ECJ_JAR).' >&2; exit 1
fi
find "$build_dir/classes" -name '*.class' > "$build_dir/class-files.txt"
"$build_tools/d8" --min-api 26 --lib "$platform_jar" --output "$build_dir/dex" @"$build_dir/class-files.txt"
cp "$build_dir/base.apk" "$build_dir/unsigned.apk"
(cd "$build_dir/dex" && zip -q "$build_dir/unsigned.apk" classes*.dex)
"$build_tools/zipalign" -f 4 "$build_dir/unsigned.apk" "$build_dir/aligned.apk"
# Keep this key locally between builds so updates can be installed over an earlier APK.
key_path="${BLOOM_SIGNING_KEY:-$project_dir/.local-signing/debug.keystore}"
mkdir -p "$(dirname "$key_path")"
if [[ ! -f "$key_path" ]]; then
  keytool -genkeypair -keystore "$key_path" -storepass android -keypass android -alias bloom \
    -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Corner Bloom Local Development'
fi
"$build_tools/apksigner" sign --ks "$key_path" --ks-pass pass:android --ks-key-alias bloom \
  --out "$build_dir/corner-bloom-$version_name.apk" "$build_dir/aligned.apk"
"$build_tools/apksigner" verify --verbose "$build_dir/corner-bloom-$version_name.apk"
echo "$build_dir/corner-bloom-$version_name.apk"
