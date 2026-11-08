#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cache_dir=${JELLYLIVE_GRADLE_CACHE:-$project_dir/.local-build/cache}
mkdir -p "$project_dir/.local-build/waydroid-review/driver"
docker --config /tmp run --pull=never --rm --user "$(id -u):$(id -g)" \
 --mount "type=bind,src=$project_dir/scripts/device,dst=/source,readonly" \
 --mount "type=bind,src=$project_dir/.local-build/waydroid-review/driver,dst=/output" \
 --mount "type=bind,src=$cache_dir,dst=/cache,readonly" \
 --entrypoint /bin/sh "${JELLYLIVE_BUILD_IMAGE:-swr.cn-north-4.myhuaweicloud.com/ddn-k8s/ghcr.io/cirruslabs/flutter:3.38.6}" -c '
 set -eu
 sdk=/opt/android-sdk-linux
 build=$sdk/build-tools/36.0.0
 android=$sdk/platforms/android-36/android.jar
 mkdir -p /tmp/probe/classes /tmp/probe/dex
 javac -source 8 -target 8 -cp "$android" -d /tmp/probe/classes /source/TvRegressionProbe.java
 jar cf /tmp/probe/classes.jar -C /tmp/probe/classes .
 "$build/d8" --lib "$android" --min-api 26 --output /tmp/probe/dex /tmp/probe/classes.jar
 "$build/aapt" package -f -M /source/AndroidManifest.xml -I "$android" -F /tmp/probe/base.apk
 cd /tmp/probe/dex
 "$build/aapt" add /tmp/probe/base.apk classes.dex
 "$build/apksigner" sign --ks /cache/signing/debug.keystore --ks-pass pass:android --key-pass pass:android --out /output/tv-regression-probe.apk /tmp/probe/base.apk
 "$build/apksigner" verify /output/tv-regression-probe.apk
 '
