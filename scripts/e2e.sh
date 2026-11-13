#!/usr/bin/env bash
# Runs the E2E and visual tests in Docker, against the image built from target/im.jar.
# Build the jar first: mvn package. Pass --update to write new screenshot baselines.
set -euo pipefail
cd "$(dirname "$0")/.."

PLAYWRIGHT_ARGS=""
if [ "${1:-}" = "--update" ]; then
    PLAYWRIGHT_ARGS="--update-snapshots"
fi
export PLAYWRIGHT_ARGS

IM_IMAGE="${IM_IMAGE:-im:test}"
export IM_IMAGE
if [ -z "${SKIP_IMAGE_BUILD:-}" ]; then
    docker build --tag "$IM_IMAGE" .
fi

compose=(docker compose --file e2e/docker-compose.yml --project-name im-e2e)
trap '"${compose[@]}" down --volumes --remove-orphans >/dev/null 2>&1' EXIT
"${compose[@]}" up --abort-on-container-exit --exit-code-from playwright
