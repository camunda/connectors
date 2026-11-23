#!/usr/bin/env bash
# Boots a Connectors runtime Docker image and fails unless it reports healthy.
#
# The unit and e2e suites run the runtime in-process on Maven's per-jar classpath,
# so they never exercise the packaged image (fat jar layout, manifest, start.sh).
# A startup crash in the shipped image (INC-9269: hibernate-validator 9.2.0 in the
# shaded 8.8 bundle) therefore passed every check. This script closes that gap.
#
# No Zeebe is started (it would cost minutes): the client points at an address nothing
# listens on. readiness/liveness therefore stay DOWN (they include zeebeClient), so we poll
# the `ping` health component instead. It answers UP only once the Spring context has
# refreshed and the web server is serving, which is the phase where classpath/packaging
# crashes like INC-9269 fail. The `ping` component exists on every release line.
#
# Usage: smoke-start-image.sh <image> [timeout-seconds]
set -euo pipefail

IMAGE="${1:?usage: $0 <image> [timeout-seconds]}"
TIMEOUT="${2:-180}"
NAME="connectors-smoke-$$"
PORT=8080
HEALTH_URL="http://localhost:${PORT}/actuator/health/ping"
SETTLE_SECONDS=10

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

dump_logs() {
  echo "::group::Container logs"
  docker logs "$NAME" 2>&1 || true
  echo "::endgroup::"
}

# No restart policy: a crashing JVM exits and we fail fast instead of waiting out the timeout.
docker run -d --name "$NAME" -p "${PORT}:8080" \
  -e CAMUNDA_CLIENT_MODE=self-managed \
  -e CAMUNDA_CLIENT_GRPC_ADDRESS=http://127.0.0.1:26500 \
  -e CAMUNDA_CLIENT_REST_ADDRESS=http://127.0.0.1:8088 \
  -e CAMUNDA_CLIENT_AUTH_METHOD=none \
  "$IMAGE" >/dev/null

echo "Waiting up to ${TIMEOUT}s for ${IMAGE} to report UP on ${HEALTH_URL}"
deadline=$((SECONDS + TIMEOUT))
while (( SECONDS < deadline )); do
  if [[ "$(docker inspect -f '{{.State.Running}}' "$NAME" 2>/dev/null)" != "true" ]]; then
    dump_logs
    echo "::error::Connectors runtime exited during startup (exit code $(docker inspect -f '{{.State.ExitCode}}' "$NAME"))"
    exit 1
  fi
  if curl -fsS "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
    echo "Connectors runtime is UP after ${SECONDS}s; checking it stays up for ${SETTLE_SECONDS}s"
    sleep "$SETTLE_SECONDS"
    if [[ "$(docker inspect -f '{{.State.Running}}' "$NAME" 2>/dev/null)" != "true" ]]; then
      dump_logs
      echo "::error::Connectors runtime exited shortly after startup"
      exit 1
    fi
    echo "Full health report (DOWN components are expected without a Zeebe broker):"
    curl -sS "http://localhost:${PORT}/actuator/health" || true
    echo
    exit 0
  fi
  sleep 3
done

dump_logs
echo "::error::Connectors runtime did not report UP within ${TIMEOUT}s"
exit 1
