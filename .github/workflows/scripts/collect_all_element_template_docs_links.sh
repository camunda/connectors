#!/bin/bash
set -e

DOCUSAURUS_BASE_URL="https://docs.camunda.io/"
GITHUB_FILE_URL="https://raw.githubusercontent.com/camunda/camunda-docs/d50f0a316cc629c43b2038d1a814a70dbac17add/connectors-element-template-links.txt" #TODO adjust this to main once merged
DOCS_REPO_LINK_FILE="connector-element-template-links-download.txt"
CURRENT_LINK_FILE="connector-element-template-links-current.txt"
NEW_LINK_FILE="connectors-element-template-links.txt"

# Clear files
: > "$DOCS_REPO_LINK_FILE"
: > "$CURRENT_LINK_FILE"
: > "$NEW_LINK_FILE"

echo "Downloading existing links file from docs repo..."
curl -sL "$GITHUB_FILE_URL" | grep "^$DOCUSAURUS_BASE_URL" > "$DOCS_REPO_LINK_FILE"


echo "Extracting links from connectors repo..."

extract_links_from_file() {
  local file_path="$1"
  grep -oE "\"$DOCUSAURUS_BASE_URL[^\"]+\"" "$file_path" | sed -E "s|\"||g; s|[\\/]$||g"
}

# Find all JSON files in "element-templates" directories, extract documentation links,
find . -type d -name "element-templates" | while read -r dir; do
  find "$dir" -type f -name "*.json" | while read -r file; do
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



