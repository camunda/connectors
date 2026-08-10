FROM mcr.microsoft.com/devcontainers/base:ubuntu

RUN apt-get update && apt-get install -y \
    git \
    curl \
    jq \
    ripgrep \
    fd-find \
    build-essential \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Native installer (no Node/npm dependency, unlike the npm package) — lands
# under /root, which is mode 0700 and unreachable for the non-root "vscode"
# remoteUser, so the real binary is copied out to a world-readable path
# instead of symlinked.
RUN curl -fsSL https://claude.ai/install.sh | bash && \
    install -m 0755 "$(readlink -f /root/.local/bin/claude)" /usr/local/bin/claude && \
    rm -rf /root/.local
