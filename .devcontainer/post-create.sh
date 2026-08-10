#!/usr/bin/env bash
set -euo pipefail

# Named volumes mount as root:root on first creation, blocking writes from
# the non-root dev user (mvnw/npm need to write into their caches).
sudo chown dev:dev ~/.m2 ~/.npm

echo "==> Git"
git --version
ssh-add -l || true

echo "==> Claude Code"
claude --version

echo "==> Toolchain"
java -version
./mvnw -v
node -v

echo "==> element-templates-cli (required by connectors-e2e-test, see AGENTS.md)"
npm install --global "element-templates-cli@$(jq -r '.devDependencies["element-templates-cli"]' .github/workflows/package.json)"
which element-templates-cli
