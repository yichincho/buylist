#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
sdk_dir="${ANDROID_SDK_ROOT:-$project_dir/android-sdk}"
build_tools="$sdk_dir/build-tools/36.0.0"
android_jar="$sdk_dir/platforms/android-36/android.jar"
out="$project_dir/build"

test -f "$android_jar"
rm -rf "$out"
mkdir -p "$out/gen" "$out/classes" "$out/dex" "$out/compiled-res" "$out/outputs/apk/debug" "$out/outputs/apk/release"

"$build_tools/aapt2" compile --dir "$project_dir/app/src/main/res" -o "$out/compiled-res/resources.zip"
"$build_tools/aapt2" link \
  -o "$out/base.apk" \
  -I "$android_jar" \
  --manifest "$project_dir/app/src/main/AndroidManifest.xml" \
  --rename-manifest-package tw.yc.smartshopping.direct \
  --java "$out/gen" \
  --min-sdk-version 24 \
  --target-sdk-version 36 \
  --version-code 4 \
  --version-name 0.2.2 \
  "$out/compiled-res/resources.zip"

find "$project_dir/app/src/main/java" "$out/gen" -name '*.java' -print0 | \
  xargs -0 javac -encoding UTF-8 -source 8 -target 8 -classpath "$android_jar" -d "$out/classes"

jar cf "$out/classes.jar" -C "$out/classes" .
"$build_tools/d8" --min-api 24 --lib "$android_jar" --output "$out/dex" "$out/classes.jar"
cp "$out/base.apk" "$out/app-unsigned.apk"
(cd "$out/dex" && zip -q -u "$out/app-unsigned.apk" classes.dex)
"$build_tools/zipalign" -f 4 "$out/app-unsigned.apk" "$out/app-aligned.apk"
cp "$out/app-aligned.apk" "$out/outputs/apk/release/app-release-unsigned.apk"

keystore="$project_dir/.keys/debug.keystore"
mkdir -p "$project_dir/.keys"
if [ ! -f "$keystore" ]; then
  keytool -genkeypair -noprompt -keystore "$keystore" -storepass android -keypass android \
    -alias androiddebugkey -dname "CN=Smart Shopping Debug,O=Android,C=TW" -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$build_tools/apksigner" sign --ks "$keystore" --ks-pass pass:android --key-pass pass:android \
  --out "$out/outputs/apk/debug/app-debug.apk" "$out/app-aligned.apk"
"$build_tools/apksigner" verify --verbose "$out/outputs/apk/debug/app-debug.apk"

mkdir -p "$out/test-classes"
javac -encoding UTF-8 -classpath "$android_jar" -d "$out/test-classes" \
  "$project_dir/app/src/main/java/tw/yc/smartshopping/LocalParser.java" \
  "$project_dir/app/src/main/java/tw/yc/smartshopping/ShoppingListModel.java" \
  "$project_dir/app/src/main/java/tw/yc/smartshopping/ShoppingListWorkflow.java" \
  "$project_dir/app/src/main/java/tw/yc/smartshopping/AiProvider.java" \
  "$project_dir/app/src/main/java/tw/yc/smartshopping/GeminiResponseParser.java" \
  "$project_dir/app/src/main/java/tw/yc/smartshopping/GeminiClient.java" \
  "$project_dir/app/src/main/java/tw/yc/smartshopping/DeepSeekClient.java" \
  "$project_dir/app/src/main/java/tw/yc/smartshopping/ShareModeResolver.java" \
  "$project_dir/tests/LocalParserTest.java" \
  "$project_dir/tests/ShoppingListModelTest.java" \
  "$project_dir/tests/ShoppingListWorkflowTest.java" \
  "$project_dir/tests/AiProviderTest.java" \
  "$project_dir/tests/GeminiResponseParserTest.java" \
  "$project_dir/tests/GeminiClientTest.java" \
  "$project_dir/tests/ShareModeResolverTest.java"
java -cp "$out/test-classes:$android_jar" tw.yc.smartshopping.LocalParserTest
java -cp "$out/test-classes:$android_jar" tw.yc.smartshopping.ShoppingListModelTest
java -cp "$out/test-classes:$android_jar" tw.yc.smartshopping.ShoppingListWorkflowTest
java -cp "$out/test-classes:$android_jar" tw.yc.smartshopping.AiProviderTest
java -cp "$out/test-classes:$android_jar" tw.yc.smartshopping.GeminiResponseParserTest
java -cp "$out/test-classes:$android_jar" tw.yc.smartshopping.GeminiClientTest
java -cp "$out/test-classes:$android_jar" tw.yc.smartshopping.ShareModeResolverTest

if rg -a -n -e 'sk-[A-Za-z0-9_-]{12,}' -e 'AIza[0-9A-Za-z_-]{20,}' \
  "$project_dir/app" "$project_dir/scripts" "$project_dir/tests" "$project_dir/docs" "$project_dir/README.md" \
  "$out/outputs/apk"; then
  echo "Potential embedded API key found" >&2
  exit 1
fi
echo "No embedded DeepSeek or Google API keys found"
echo "APK: $out/outputs/apk/debug/app-debug.apk"
