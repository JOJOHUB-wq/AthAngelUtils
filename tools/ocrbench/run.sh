#!/usr/bin/env bash
# Offline benchmark runner for the mod OCR. Bootstraps a JDK via a sparse AOSP clone
# (only github.com is reachable in the sandbox), then compiles the REAL mod OCR sources
# (CaptchaOcr.java, MlpOcr.java) together with offline stubs and runs OcrBench.
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
JDK="${JDK:-/tmp/jdk17/linux-x86}"
if [ ! -x "$JDK/bin/javac" ]; then
  echo "bootstrapping JDK (sparse clone) ..." >&2
  rm -rf /tmp/jdk17
  git clone --quiet --depth 1 --filter=blob:none --sparse \
    https://github.com/msft-mirror-aosp/platform.prebuilts.jdk.jdk17 /tmp/jdk17 >&2
  (cd /tmp/jdk17 && git sparse-checkout set linux-x86) >&2
fi
OUT=/tmp/ocrbench-out
rm -rf "$OUT" && mkdir -p "$OUT"
SRC="$REPO/src/client/java"
STUB="$REPO/tools/ocrbench/stubs"
BENCH="$REPO/tools/ocrbench/src"
"$JDK/bin/javac" -nowarn -encoding UTF-8 -d "$OUT" \
  "$STUB/net/fabricmc/loader/api/FabricLoader.java" \
  "$STUB/ua/atherium/agnelutils/AthAgnelUtils.java" \
  "$STUB/com/google/gson/JsonElement.java" \
  "$STUB/com/google/gson/JsonPrimitive.java" \
  "$STUB/com/google/gson/JsonObject.java" \
  "$STUB/com/google/gson/JsonArray.java" \
  "$STUB/com/google/gson/JsonParser.java" \
  "$SRC/ua/atherium/agnelutils/client/flow/CaptchaOcr.java" \
  "$SRC/ua/atherium/agnelutils/client/flow/MlpOcr.java" \
  "$BENCH/OcrBench.java"
echo "compiled OK"
"$JDK/bin/java" -cp "${EXTRA_CP:+$EXTRA_CP:}$OUT:$REPO/src/client/resources" ua.atherium.agnelutils.client.flow.OcrBench "$@"
