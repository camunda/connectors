#!/bin/bash

FROM_VERSION="$1"
TO_VERSION="$2"

if [ -z "$FROM_VERSION" ] || [ -z "$TO_VERSION" ]; then
  echo "Usage: $0 <FROM_VERSION> <TO_VERSION>"
  exit 1
fi

update_links_in_file() {
  local file_path="$1"

  # Escape dots in versions for use in regex
  escaped_from_version=$(printf '%s\n' "$FROM_VERSION" | sed 's/\./\\./g')
  escaped_to_version=$(printf '%s\n' "$TO_VERSION" | sed 's/\./\\./g')

  sed -i -E "s|(https://docs.camunda.io/docs/)$escaped_from_version/|\1$escaped_to_version/|g" "$file_path"
}

# Find all JSON files in "element-templates" directories (excluding versioned subdirs)
find . -type d -name "element-templates" | while read -r dir; do
  find "$dir" -path "$dir/versioned" -prune -o -type f -name "*.json" -print | while read -r file; do
    echo "Updating links in: $file"
    update_links_in_file "$file"
  done
done

find . -type f -name "*.java" | while read -r file; do
  if grep -q "https://docs.camunda.io/docs/$FROM_VERSION/" "$file"; then
    echo "Updating links in Java: $file"
    update_links_in_file "$file"
  fi
done