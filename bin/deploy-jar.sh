#!/bin/bash

set -euo pipefail

echo "building jar..."
clojure -Spom
clojure -A:jar

echo "deploying..."
mvn deploy:deploy-file -Dfile=lib.jar -DrepositoryId=clojars -Durl=https://clojars.org/repo -DpomFile=pom.xml