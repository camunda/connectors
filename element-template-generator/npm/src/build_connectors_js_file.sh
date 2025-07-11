#!/bin/bash

echo "Building JS connectors file..."

cd src
cp connectors-template.js connectors.js
DIR="element-templates"

importString=""
arrayString=""

for file in "$DIR"/*; do
  if [ -f "$file" ]; then
    filename=$(basename "$file")
    connector_name="${filename//.json/}"
    connector_js_save_name=$(echo "$connector_name" | sed 's/[^a-zA-Z0-9]/_/g')
    importString+="import $connector_js_save_name from './element-templates/$filename' with { type: \"json\" };"
    arrayString+="$connector_js_save_name,"
  fi
done

sed -i "s#/\* pre-build script will write imports here \*/#${importString}#g" connectors.js
sed -i "s#/\* pre-build script will write array content here \*/#${arrayString}#g" connectors.js

echo "Building JS connectors file completed."


