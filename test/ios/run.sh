#!/bin/bash
# iOS route tests: builds the real ios/RNInCallManager/RNInCallManager.m with RouteTests.m (a fake
# AVAudioSession, see the top of that file) as a Mac Catalyst command-line binary and runs each
# scenario in its own process. macOS with Xcode only.
#
#   bash test/ios/run.sh                 every scenario
#   bash test/ios/run.sh <scenario>...   some of them (bash test/ios/run.sh --list names them)
#   INCALL_M=<file.m> bash test/ios/run.sh   test another copy of RNInCallManager.m
#
# First checks that the podspec's source_files are the library's own (test/ios/podspec_check.rb).
#
# The binary and the logs go to test/ios/build/ (ignored by git).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
SRC="${INCALL_M:-$ROOT/ios/RNInCallManager/RNInCallManager.m}"
OUT="$HERE/build"
BIN="$OUT/route-tests"
mkdir -p "$OUT/logs"

# the pod compiles only the library's sources, not these tests (RouteTests.m has a main)
ruby "$HERE/podspec_check.rb"

SDK="$(xcrun --sdk macosx --show-sdk-path)"
xcrun clang -fobjc-arc -fmodules -target arm64-apple-ios17.0-macabi -isysroot "$SDK" \
  -iframework "$SDK/System/iOSSupport/System/Library/Frameworks" \
  -Wall -Wextra -Wno-unused-parameter -Wno-unused-function -Werror \
  -I "$HERE/stubs" -I "$ROOT/ios/RNInCallManager" \
  -framework Foundation -framework AVFoundation -framework UIKit \
  -o "$BIN" "$HERE/RouteTests.m" "$SRC"

if [ "$#" -eq 1 ] && [ "$1" = "--list" ]; then
  "$BIN" --list
  exit 0
fi
if [ "$#" -gt 0 ]; then
  SCENARIOS=("$@")
else
  SCENARIOS=()
  while IFS= read -r name; do SCENARIOS+=("$name"); done < <("$BIN" --list)
fi

failed=0
for sc in "${SCENARIOS[@]}"; do
  LOG="$OUT/logs/$sc.log"
  if "$BIN" "$sc" > "$LOG" 2>&1; then
    echo "ok   $sc"
  else
    echo "FAIL $sc (log: $LOG)"
    grep -E 'check failed' "$LOG" | sed 's/^/       /' || true
    failed=$((failed + 1))
  fi
done
echo "${#SCENARIOS[@]} scenarios, $failed failed"
[ "$failed" -eq 0 ]
