#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# Docker Desktop (macOS/Windows/Linux) runs the daemon inside its own VM, so the
# host's real SSH_AUTH_SOCK path isn't reachable from a container — Docker
# Desktop bridges it at this fixed, always-present path instead. Plain Docker
# Engine (no Desktop layer, e.g. native Linux) has no such VM boundary or
# bridge, so the host's actual SSH_AUTH_SOCK bind-mounts directly and correctly.
if docker info --format '{{.OperatingSystem}}' 2>/dev/null | grep -qi "docker desktop"; then
  SSH_AUTH_SOCK_MOUNT="/run/host-services/ssh-auth.sock"
else
  : "${SSH_AUTH_SOCK:?SSH agent not detected: SSH_AUTH_SOCK is not set. Start ssh-agent (or your SSH key manager) before opening the dev container.}"
  SSH_AUTH_SOCK_MOUNT="$SSH_AUTH_SOCK"
fi

{
  echo "SSH_AUTH_SOCK_MOUNT=${SSH_AUTH_SOCK_MOUNT}"
  echo "HOST_HOME=${HOME}"
} > .env
