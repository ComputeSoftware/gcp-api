#!/bin/bash

set -euo pipefail

clojure -T:build jar
clojure -T:build deploy
