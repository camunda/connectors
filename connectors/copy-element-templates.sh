#!/bin/bash
# This script copies all element templates from the connectors directory to the Camunda Desktop Modeler's element templates directory.

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

# Find all .json files in element-templates directories and copy them to /tmp/json
find . -type d -name "element-templates" | while read -r dir; do
  find "$dir" -type f -name "*.json" -exec cp {} ~/Library/Application\ Support/camunda-modeler/resources/element-templates/ \;
  echo -e "${YELLOW}Copied ETs from [$dir] => ~/Library/Application\ Support/camunda-modeler/resources/element-templates${NC}"
done

echo -e "${GREEN}All element templates have been copied to ~/Library/Application\ Support/camunda-modeler/resources/element-templates${NC}"
