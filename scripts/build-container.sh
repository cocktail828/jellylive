#!/bin/sh
set -eu
umask 077
client_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
version=$(sed -n 's/^jellyfin.version=//p' "$client_dir/gradle.properties" | tail -1)
version=${version#v}
# Configs are read-only container inputs. Never copy secrets into the source tree.
for name in bestv-bootstrap.json jellyfin-bootstrap.json; do
  test ! -e "$client_dir/app/src/main/assets/$name" || {
    echo "Remove the old source asset $name; use .local-build/bootstrap instead." >&2; exit 1;
  }
done
output_dir="$client_dir/dist"
output_suffix=""
set --
case "${JELLYLIVE_EMBED_CONFIG:-1}" in
  0) ;;
  1)
    for kind in box library; do
      if test "$kind" = box; then
        name=bestv-bootstrap.json
        explicit=${BESTV_EMBED_CONFIG:-}
      else
        name=jellyfin-bootstrap.json
        explicit=${BESTV_EMBED_LIBRARY_CONFIG:-}
      fi
      config_path=${explicit:-$client_dir/.local-build/bootstrap/$name}
      if ! test -f "$config_path"; then
        test -z "$explicit" || { echo "Explicit $kind bootstrap file is missing." >&2; exit 1; }
        continue
      fi
      config_dir=$(CDPATH= cd -- "$(dirname -- "$config_path")" && pwd)
      config_path="$config_dir/$(basename -- "$config_path")"
      case "$config_path" in
        "$client_dir"/*)
          if git -C "$client_dir" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
            git -C "$client_dir" check-ignore -q -- "$config_path" || {
              echo 'Local bootstrap input inside the repository must be untracked and Git-ignored.' >&2; exit 1;
            }
          fi ;;
      esac
      set -- "$@" --mount "type=bind,src=$config_path,dst=/bootstrap/$name,readonly"
      output_dir="$client_dir/private"
      output_suffix="-configured"
    done ;;
  *) echo 'JELLYLIVE_EMBED_CONFIG must be 0 or 1.' >&2; exit 1 ;;
esac
cache_dir=${JELLYLIVE_GRADLE_CACHE:-$client_dir/.local-build/cache}
mkdir -p "$cache_dir/signing" "$output_dir" "$client_dir/app/build"
chmod 700 "$cache_dir/signing"
if test -n "$output_suffix"; then chmod 700 "$output_dir" "$client_dir/app/build"; fi
docker run --pull=never --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -e GRADLE_USER_HOME=/cache -e BESTV_BUILD_VERSION="$version" \
  -e JELLYLIVE_CONFIGURED="$output_suffix" \
  --mount "type=bind,src=$client_dir,dst=/src" \
  --mount "type=bind,src=$cache_dir,dst=/cache" \
  "$@" --workdir /src --entrypoint /bin/sh \
  "${JELLYLIVE_BUILD_IMAGE:-swr.cn-north-4.myhuaweicloud.com/ddn-k8s/ghcr.io/cirruslabs/flutter:3.38.6}" -c '
    set -eu
    if ! test -f /cache/signing/debug.keystore; then
      keytool -genkeypair -keystore /cache/signing/debug.keystore -storepass android \
        -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Bestv Live Debug,O=Local,C=CN"
      chmod 600 /cache/signing/debug.keystore
    fi
    set -- :app:assembleDebug -Pjellylive.debugKeystore=/cache/signing/debug.keystore --no-daemon --console plain
    if test -n "$JELLYLIVE_CONFIGURED"; then set -- "$@" -Pjellylive.bootstrapDir=/bootstrap; fi
    ./gradlew "$@"
    /opt/android-sdk-linux/build-tools/36.0.0/apksigner verify \
      "app/build/outputs/apk/debug/jellyfin-androidtv-v$BESTV_BUILD_VERSION-debug.apk"
  '
output_path="$output_dir/BestvLive-v$version$output_suffix.apk"
cp "$client_dir/app/build/outputs/apk/debug/jellyfin-androidtv-v$version-debug.apk" "$output_path.tmp"
if test -n "$output_suffix"; then chmod 600 "$output_path.tmp"; else chmod 644 "$output_path.tmp"; fi
mv -f "$output_path.tmp" "$output_path"
printf 'APK: %s\n' "$output_path"
