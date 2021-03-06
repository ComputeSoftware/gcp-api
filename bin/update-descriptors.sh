#!/bin/bash

set -euox pipefail

dir="gcp-api-descriptors"
branch=$(git rev-parse --abbrev-ref HEAD)

rm -rf $dir

echo "Cloning gcp-api-descriptors branch:${branch}"
git clone git@github.com:ComputeSoftware/gcp-api-descriptors.git $dir
cd $dir
git checkout "${branch}"
git pull origin "${branch}"
cd ..

echo "Updating descriptor files..."
clojure -M:dev -m update-descriptors $dir

cd $dir

if [[ $(git status --porcelain) ]]; then
  echo "Changes dectected. Updating descriptors..."
  git add -A .
  git commit -a -m "Update descriptor files"
  git push -u origin "${branch}"
else
  echo "No changes to push."
fi