#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cache_dir=${JELLYLIVE_GRADLE_CACHE:-$project_dir/.local-build/cache}
docker run --pull=never --rm --user "$(id -u):$(id -g)" -e HOME=/tmp \
  --mount "type=bind,src=$project_dir,dst=/src,readonly" \
  --mount "type=bind,src=$cache_dir,dst=/cache,readonly" \
  --workdir /src --entrypoint /bin/sh \
  "${JELLYLIVE_BUILD_IMAGE:-swr.cn-north-4.myhuaweicloud.com/ddn-k8s/ghcr.io/cirruslabs/flutter:3.38.6}" -c '
    set -eu
    python3 scripts/checks/BuildBootstrapCheck.py
    check_lib=$(find /cache/wrapper/dists -type d -path "*/gradle-9.1.0/lib" -print -quit)
    test -n "$check_lib" || { echo "Run the container build first to populate the Gradle cache." >&2; exit 1; }
    java -cp "$check_lib/*" groovy.ui.GroovyMain scripts/checks/LiveInteractionCheck.groovy
    javac -proc:none -d /tmp/live-gesture-check app/src/main/java/org/jellyfin/androidtv/bestv/LiveConfirmGesture.java scripts/checks/LiveConfirmGestureCheck.java
    java -cp /tmp/live-gesture-check LiveConfirmGestureCheck
    java -cp "$check_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 \
      -classpath "$check_lib/kotlin-stdlib-2.2.0.jar" -d /tmp/library-bootstrap-check \
      app/src/main/java/org/jellyfin/androidtv/bestv/LibraryBootstrap.kt scripts/checks/LibraryBootstrapCheck.kt
    java -cp "/tmp/library-bootstrap-check:$check_lib/kotlin-stdlib-2.2.0.jar" org.jellyfin.androidtv.bestv.LibraryBootstrapCheckKt
    sed -n "/^fun getVersionCode(/,\$p" buildSrc/src/main/kotlin/VersionUtils.kt > /tmp/NumericVersionCheck.kt
    cat scripts/checks/NumericVersionCheck.kt >> /tmp/NumericVersionCheck.kt
    java -cp "$check_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 \
      -classpath "$check_lib/kotlin-stdlib-2.2.0.jar" -d /tmp/numeric-version-check /tmp/NumericVersionCheck.kt
    java -cp "/tmp/numeric-version-check:$check_lib/kotlin-stdlib-2.2.0.jar" NumericVersionCheckKt
  '
