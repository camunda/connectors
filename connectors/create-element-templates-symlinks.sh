#!/bin/bash
# This script creates symlinks for all element templates in the current directory to the Camunda Modeler's element templates directory.

# Define color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get the directory where the script is located
SCRIPT_PATH="$(readlink -f "$0")"
SCRIPT_DIR="$(dirname "$SCRIPT_PATH")"

# Change to the script's directory
cd "$SCRIPT_DIR" || exit

echo -e "${YELLOW}NOTE: This script can be run only once. Until you create new element templates, you don't need to run this script again.${NC}"
echo -e "${BLUE}Starting to copy element templates to ~/Library/Application\ Support/camunda-modeler/resources/element-templates${NC}"
# Find all .json files in element-templates directories and copy them to /tmp/json
find . -type d -name "element-templates" | while read -r dir; do
  echo -e "${YELLOW}Creating symlink for [$dir]${NC}"
  find "$dir" -type f -name "*.json" | grep -v "hybrid" | while read -r file; do
    dest_dir=~/Library/Application\ Support/camunda-modeler/resources/element-templates/
    dest_file="$dest_dir$(basename "$file")"
    # Remove the existing symlink if it exists
    if [ -L "$dest_file" ]; then
      rm "$dest_file"
    fi
    # Create the new symlink
    ln -s "$SCRIPT_DIR/$file" "$dest_file"
  done
done

echo -e "${GREEN}All element templates have been copied to ~/Library/Application\ Support/camunda-modeler/resources/element-templates${NC}"
