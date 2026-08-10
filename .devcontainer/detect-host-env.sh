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

# SSH-based commit signing (git config gpg.format=ssh) needs its public key
# file visible inside the container at the same absolute path git expects
# (user.signingkey) — signing itself rides on the SSH_AUTH_SOCK forward above
# for free. This differs per developer, so it can't be a fixed line in the
# committed compose file; look it up from this host's own git config instead.
# Falls back to a harmless /dev/null mount for anyone not using SSH signing.
GPG_FORMAT="$(git config --global --get gpg.format || true)"
SIGNING_KEY="$(git config --global --get user.signingkey || true)"
if [ "$GPG_FORMAT" = "ssh" ] && [ -n "$SIGNING_KEY" ] && [ -f "$SIGNING_KEY" ]; then
  SIGNING_PUBKEY_MOUNT_SRC="$SIGNING_KEY"
  SIGNING_PUBKEY_MOUNT_DST="$SIGNING_KEY"
else
  SIGNING_PUBKEY_MOUNT_SRC="/dev/null"
  SIGNING_PUBKEY_MOUNT_DST="/tmp/no-ssh-signing-key-configured"
fi

{
  echo "SSH_AUTH_SOCK_MOUNT=${SSH_AUTH_SOCK_MOUNT}"
  echo "HOST_HOME=${HOME}"
  echo "SIGNING_PUBKEY_MOUNT_SRC=${SIGNING_PUBKEY_MOUNT_SRC}"
  echo "SIGNING_PUBKEY_MOUNT_DST=${SIGNING_PUBKEY_MOUNT_DST}"
} > .env
