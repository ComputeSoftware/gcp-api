#!/bin/bash

set -euo pipefail

dir="gcp-api-descriptors"

rm -rf $dir

git clone git@github.com:ComputeSoftware/gcp-api-descriptors.git $dir

clojure -A:dev -m update-api-descriptors $dir

cd $dir

git add -A .

git commit -a -m "Update descriptor files"
git push