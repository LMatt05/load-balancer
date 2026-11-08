#!/usr/bin/env bash
# Usage: [RATE=500] [DURATION=30s] bash loadtest/run.sh <name> <target-url>
set -euo pipefail
export MSYS_NO_PATHCONV=1   # stops Git Bash rewriting /paths passed to docker

name="${1:?usage: run.sh <name> <target-url>}"
target="${2:?usage: run.sh <name> <target-url>}"

mkdir -p loadtest/results
docker compose -f docker-compose.yaml -f docker-compose.loadtest.yaml run --rm k6 run \
  -e TARGET="$target" \
  -e RATE="${RATE:-500}" \
  -e DURATION="${DURATION:-30s}" \
  --summary-export="/loadtest/results/${name}.json" \
  /loadtest/script.js