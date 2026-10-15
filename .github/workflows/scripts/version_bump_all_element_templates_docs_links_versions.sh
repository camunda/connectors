#!/bin/bash

# Rewrites every versioned docs link (https://docs.camunda.io/docs/<major>.<minor>/) that is older
# than TO_VERSION to TO_VERSION, so links that missed a release cycle are not stranded.
# Links on other hosts (e.g. unsupported.docs.camunda.io) and unversioned links are left untouched.

TO_VERSION="$1"

if ! [[ "$TO_VERSION" =~ ^([0-9]+)\.([0-9]+)$ ]]; then
  echo "Usage: $0 <TO_VERSION>   (e.g. 8.10)"
  exit 1
fi

export TO_MAJOR="${BASH_REMATCH[1]}"
export TO_MINOR="${BASH_REMATCH[2]}"

update_links_in_file() {
  local file_path="$1"

  perl -pi -e '
    s{(https://docs\.camunda\.io/docs/)(\d+)\.(\d+)/}{
      ($2 < $ENV{TO_MAJOR} || ($2 == $ENV{TO_MAJOR} && $3 < $ENV{TO_MINOR}))
        ? "$1$ENV{TO_MAJOR}.$ENV{TO_MINOR}/"
        : "$1$2.$3/"
    }ge
  ' "$file_path"
}

# Find all JSON files in "element-templates" directories (excluding versioned subdirs)
find . -type d -name "element-templates" | while read -r dir; do
  find "$dir" -path "$dir/versioned" -prune -o -type f -name "*.json" -print | while read -r file; do
    echo "Updating links in: $file"
    update_links_in_file "$file"
  done
done

find . -type f -name "*.java" | while read -r file; do
  if grep -qE "https://docs\.camunda\.io/docs/[0-9]+\.[0-9]+/" "$file"; then
    echo "Updating links in Java: $file"
    update_links_in_file "$file"
  fi
done
