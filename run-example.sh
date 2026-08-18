#!/usr/bin/env sh
set -eu

OUT="${TMPDIR:-/tmp}/tenant-pod-classes"
mkdir -p "$OUT"
javac -d "$OUT" $(find src/main/java -name '*.java')
java -cp "$OUT" dev.ledgerlogistics.PodIntake "${1:-tenant_acme}" "${2:-SHP_2048}" "${3:-EVT_901}" "${4:-240000}" "${5:-false}"
