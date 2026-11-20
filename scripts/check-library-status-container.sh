#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cache_dir=${JELLYLIVE_GRADLE_CACHE:-$(readlink -f "$project_dir/.local-build/cache")}
docker run --pull=never --rm --network none --user "$(id -u):$(id -g)" -e HOME=/tmp \
 --mount "type=bind,src=$project_dir,dst=/src,readonly" \
 --mount "type=bind,src=$cache_dir,dst=/cache,readonly" \
 --workdir /src --entrypoint /bin/sh \
 "${JELLYLIVE_BUILD_IMAGE:-swr.cn-north-4.myhuaweicloud.com/ddn-k8s/ghcr.io/cirruslabs/flutter:3.38.6}" -c '
 set -eu
 check_lib=$(find /cache/wrapper/dists -type d -path "*/gradle-9.1.0/lib" -print -quit)
 check_cp=/src/app/build/tmp/kotlin-classes/debug:/src/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes
 for check_jar in $(find /cache/caches/modules-2/files-2.1 -name "*.jar"); do check_cp=$check_cp:$check_jar; done
 java -cp "$check_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$check_cp" -d /tmp/status-check /src/scripts/checks/LibraryUserStatusCheck.kt
 java -cp "/tmp/status-check:$check_cp" org.jellyfin.androidtv.bestv.LibraryUserStatusCheckKt
 '
