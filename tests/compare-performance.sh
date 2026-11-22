#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 2 ]]; then echo "Usage: $0 PATH_TO_1.2_PROJECT OUTPUT_DIRECTORY" >&2; exit 2; fi
current=$(cd "$(dirname "$0")/.." && pwd)
baseline=$(cd "$1" && pwd)
mkdir -p "$2"
results=$(cd "$2" && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/app"
cp "$baseline/settings.gradle" "$baseline/gradle.properties" "$tmp/"
cp "$baseline/app/build.gradle" "$tmp/app/"
cp -R "$baseline/app/src" "$tmp/app/"
cp -R "$current/benchmark" "$tmp/benchmark"
cp -R "$current/app/src/performance" "$tmp/app/src/"
sed -n '/^android.buildTypes {$/,$p' "$current/app/build.gradle" >> "$tmp/app/build.gradle"
echo "include ':benchmark'" >> "$tmp/settings.gradle"
for run in before after; do
  project=$current
  if [[ "$run" == before ]]; then project=$tmp; fi
  gradle -p "$project" --no-daemon :benchmark:connectedPerformanceAndroidTest
  mkdir -p "$results/$run"
  cp -R "$project/benchmark/build/outputs/connected_android_test_additional_output/." "$results/$run/"
done
