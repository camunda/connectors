#!/bin/bash

# Define the source script and destination
SCRIPT_NAME="copy-element-templates.sh"
DEST_DIR="/usr/local/bin"
LINK_NAME="copy-all-ets"
LINK_PATH="$DEST_DIR/$LINK_NAME"

# Check if a symlink already exists and remove it
if [ -L "$LINK_PATH" ]; then
  sudo rm "$LINK_PATH"
fi

# Create a symbolic link
sudo ln -s "$(pwd)/$SCRIPT_NAME" "$LINK_PATH"

echo "Symbolic link has been created at $LINK_PATH"
