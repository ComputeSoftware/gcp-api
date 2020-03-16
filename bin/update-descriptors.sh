#!/bin/bash

set -euo pipefail

dir="gcp-api-descriptors"

rm -rf $dir

git clone git@github.com:ComputeSoftware/gcp-api-descriptors.git $dir

echo "Updating descriptor files..."
clojure -A:dev -m update-api-descriptors $dir

cd $dir

if [[ $(git status --porcelain) ]]; then
  echo "Changes dectected. Updating descriptors..."
  git add -A .
  git commit -a -m "Update descriptor files"
  git push
else
  echo "No changes to push."
fi