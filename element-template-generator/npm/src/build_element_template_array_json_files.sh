#!/bin/bash

OUTPUT_DIR=".temp-npm"
IGNORE_FILE="../ignore-templates.json"
cd ../../connectors

# Ensure fresh build
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

mapfile -t IGNORED_IDS < <(jq -r '.[]' "$IGNORE_FILE")

echo "Building array files for element templates..."
# Merge all element template json files of one connector into one array
find . -type d -name "element-templates" | while read -r dir; do
  find "$dir" -path "$dir/hybrid" -prune -o -type f -name "*.json" -print | while read -r file; do
    ID=$(jq -r '.id // empty' "$file")
    if [[ " ${IGNORED_IDS[*]} " =~ " $ID " ]]; then
      echo "Skipping ignored template $file"
      continue
    fi

    filename_only=$(basename "$file")
    base_name=$(echo "$filename_only" | sed -E 's/-[0-9]+\.json$/.json/')

    if [ ! -s "$OUTPUT_DIR/$base_name" ]; then # file doesn't exist or is empty
      echo "[" > "$OUTPUT_DIR/$base_name"
    else
      echo "," >> "$OUTPUT_DIR/$base_name"
    fi

    cat "$file" >> "$OUTPUT_DIR/$base_name"
  done
done

for file in "$OUTPUT_DIR"/*; do
  if [ -f "$file" ]; then
    echo "]" >> "$file"
  fi
done

mkdir -p ../element-template-generator/npm/src/element-templates
mv .temp-npm/* ../element-template-generator/npm/src/element-templates

echo "Building array files for element templates completed."

