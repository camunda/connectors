FROM ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    git \
    curl \
    jq \
    ripgrep \
    fd-find \
    build-essential \
    ca-certificates \
    sudo \
    && rm -rf /var/lib/apt/lists/*

# devcontainer features (java/node/docker-outside-of-docker) and postCreate/
# postStart both expect a non-root "dev" user with passwordless sudo — plain
# ubuntu has no such user, so it's created explicitly.
ARG USERNAME=dev
ARG USER_UID=1000
ARG USER_GID=$USER_UID
# ubuntu:24.04 ships a default "ubuntu" user at this same uid/gid (for
# cloud-init) — clear it first so dev can take uid/gid 1000 instead.
RUN userdel -r ubuntu 2>/dev/null || true \
    && groupdel ubuntu 2>/dev/null || true \
    && groupadd --gid $USER_GID $USERNAME \
    && useradd --uid $USER_UID --gid $USER_GID -m -s /bin/bash $USERNAME \
    && echo "$USERNAME ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME

# Native installer (no Node/npm dependency, unlike the npm package) — lands
# under /root, which is mode 0700 and unreachable for the non-root "dev"
# remoteUser, so the real binary is copied out to a world-readable path
# instead of symlinked.
RUN curl -fsSL https://claude.ai/install.sh | bash && \
    install -m 0755 "$(readlink -f /root/.local/bin/claude)" /usr/local/bin/claude && \
    rm -rf /root/.local
