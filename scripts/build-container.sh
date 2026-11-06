#!/bin/sh
set -eu
client_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
version=$(sed -n 's/^jellyfin.version=//p' "$client_dir/gradle.properties" | tail -1)
version=${version#v}
bootstrap_path="$client_dir/app/src/main/assets/bestv-bootstrap.json"
library_bootstrap_path="$client_dir/app/src/main/assets/jellyfin-bootstrap.json"
created_box_asset=0
created_library_asset=0
cleanup() {
  if test "$created_box_asset" = 1; then rm -f "$bootstrap_path"; fi
  if test "$created_library_asset" = 1; then rm -f "$library_bootstrap_path"; fi
}
trap cleanup EXIT
output_dir="$client_dir/dist"
output_suffix=""
if test -n "${BESTV_EMBED_CONFIG:-}"; then
  test -f "$BESTV_EMBED_CONFIG"
  test ! -e "$bootstrap_path" || { echo 'Bootstrap asset already exists; refusing to overwrite it.' >&2; exit 1; }
  mkdir -p "$(dirname "$bootstrap_path")" "$client_dir/private" "$client_dir/app/build"
  chmod 700 "$client_dir/private" "$client_dir/app/build"
  cp "$BESTV_EMBED_CONFIG" "$bootstrap_path"
  created_box_asset=1
  chmod 600 "$bootstrap_path"
  output_dir="$client_dir/private"
  output_suffix="-configured"
fi
if test -n "${BESTV_EMBED_LIBRARY_CONFIG:-}"; then
  test -f "$BESTV_EMBED_LIBRARY_CONFIG"
  test ! -e "$library_bootstrap_path" || { echo 'Library bootstrap asset already exists; refusing to overwrite it.' >&2; exit 1; }
  mkdir -p "$(dirname "$library_bootstrap_path")" "$client_dir/private" "$client_dir/app/build"
  chmod 700 "$client_dir/private" "$client_dir/app/build"
  cp "$BESTV_EMBED_LIBRARY_CONFIG" "$library_bootstrap_path"
  created_library_asset=1
  chmod 600 "$library_bootstrap_path"
  output_dir="$client_dir/private"
  output_suffix="-configured"
fi
mkdir -p "${JELLYLIVE_GRADLE_CACHE:-$client_dir/.local-build/cache}/signing" "$client_dir/dist"
chmod 700 "${JELLYLIVE_GRADLE_CACHE:-$client_dir/.local-build/cache}/signing"
docker run --pull=never --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -e GRADLE_USER_HOME=/cache -e BESTV_BUILD_VERSION="$version" \
  --mount "type=bind,src=$client_dir,dst=/src" \
  --mount "type=bind,src=${JELLYLIVE_GRADLE_CACHE:-$client_dir/.local-build/cache},dst=/cache" \
  --workdir /src --entrypoint /bin/sh \
  "${JELLYLIVE_BUILD_IMAGE:-swr.cn-north-4.myhuaweicloud.com/ddn-k8s/ghcr.io/cirruslabs/flutter:3.38.6}" -c '
    set -eu
    if ! test -f /cache/signing/debug.keystore; then
      keytool -genkeypair -keystore /cache/signing/debug.keystore -storepass android \
        -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Bestv Live Debug,O=Local,C=CN"
      chmod 600 /cache/signing/debug.keystore
    fi
    ./gradlew :app:assembleDebug -Pjellylive.debugKeystore=/cache/signing/debug.keystore --no-daemon --console plain
    /opt/android-sdk-linux/build-tools/36.0.0/apksigner verify \
      "app/build/outputs/apk/debug/jellyfin-androidtv-v$BESTV_BUILD_VERSION-debug.apk"
  '
output_path="$output_dir/BestvLive-v$version$output_suffix.apk"
cp "$client_dir/app/build/outputs/apk/debug/jellyfin-androidtv-v$version-debug.apk" "$output_path.tmp"
if test -n "$output_suffix"; then chmod 600 "$output_path.tmp"; fi
mv -f "$output_path.tmp" "$output_path"
printf 'APK: %s\n' "$output_path"
