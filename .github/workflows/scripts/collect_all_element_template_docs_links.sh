#!/bin/bash
set -e

DOCUSAURUS_BASE_URL="https://docs.camunda.io/"
GITHUB_FILE_URL="https://raw.githubusercontent.com/camunda/camunda-docs/refs/heads/main/connectors-element-template-links.txt"
DOCS_REPO_LINK_FILE="connector-element-template-links-download.txt"
CURRENT_LINK_FILE="connector-element-template-links-current.txt"
NEW_LINK_FILE="connectors-element-template-links.txt"

# Clear files
: > "$DOCS_REPO_LINK_FILE"
: > "$CURRENT_LINK_FILE"
: > "$NEW_LINK_FILE"

for arg in "$@"; do
  if [[ "$arg" == "--current-only" ]]; then
    skip_download=true
    break
  fi
done

if [[ "$skip_download" != true ]]; then
    echo "Downloading existing links file from docs repo..."
    curl -sL "$GITHUB_FILE_URL" | grep "^$DOCUSAURUS_BASE_URL" > "$DOCS_REPO_LINK_FILE"
fi

echo "Extracting links from connectors repo..."

extract_links_from_file() {
  local file_path="$1"
  grep -oE "\"$DOCUSAURUS_BASE_URL[^\"]+\"" "$file_path" | sed -E "s|\"||g; s|[\\/]$||g"
}

# Find all JSON files in "element-templates" directories (except in the versioned subdirectory), extract documentation links,
find . -type d -name "element-templates" | while read -r dir; do
  find "$dir" -path "$dir/versioned" -prune -o -type f -name "*.json" -print | while read -r file; do
    extract_links_from_file "$file"
  done
done | sort -u >> "$CURRENT_LINK_FILE"  # Sort & remove duplicates before writing


echo "Merging links from docs file and new file..."
cat "$CURRENT_LINK_FILE" "$DOCS_REPO_LINK_FILE" | sort -u > "$NEW_LINK_FILE"

echo -e "# This file contains links from connectors element templates to the documentation
# This file is used to check that we don't accidentally break these links\n\n$(cat "$NEW_LINK_FILE")" > "$NEW_LINK_FILE"

rm $DOCS_REPO_LINK_FILE
rm $CURRENT_LINK_FILE

echo "Script completed successfully."
exit 0



