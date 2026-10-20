#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ ! -f keystore.properties ]; then
  echo "keystore.properties is missing; create it with storeFile/storePassword/keyAlias/keyPassword" >&2
  exit 1
fi

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export JAVA_HOME

./gradlew :app:assembleRelease

APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK" ]; then
  echo "missing release APK: $APK" >&2
  exit 1
fi

VERSION_CODE="$(awk -F= '/^versionCode=/{print $2}' version.properties)"
VERSION_NAME="$(awk -F= '/^versionName=/{print $2}' version.properties)"
SIZE="$(stat -c%s "$APK")"
SHA="$(sha256sum "$APK" | awk '{print $1}')"
BASE="${XBOT_UPDATE_BASE:-http://192.168.0.103:9000/uploads/share/android}"
OBJECT="xbot-android-${VERSION_CODE}.apk"
CHANGELOG="${CHANGELOG:-}"

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

python3 - "$TMPDIR/latest.json" "$VERSION_CODE" "$VERSION_NAME" "$SIZE" "$SHA" "$BASE/$OBJECT" "$CHANGELOG" <<'PY'
import datetime
import json
import sys

path, code, name, size, sha, url, changelog = sys.argv[1:8]
with open(path, "w", encoding="utf-8") as fh:
    json.dump(
        {
            "versionCode": int(code),
            "versionName": name,
            "minSdk": 26,
            "size": int(size),
            "sha256": sha,
            "url": url,
            "changelog": changelog,
            "mandatory": False,
            "publishedAt": datetime.datetime.now(datetime.timezone.utc).strftime(
                "%Y-%m-%dT%H:%M:%SZ"
            ),
        },
        fh,
        ensure_ascii=False,
        indent=2,
    )
PY

mc cp --attr "Content-Type=application/vnd.android.package-archive" \
  "$APK" "xbotlan/uploads/share/android/$OBJECT"
mc cp --attr "Content-Type=application/json" \
  "$TMPDIR/latest.json" "xbotlan/uploads/share/android/latest.json"
mc anonymous set download xbotlan/uploads/share/android

echo "published $VERSION_NAME ($VERSION_CODE) sha256=$SHA"
echo "manifest: $BASE/latest.json"
