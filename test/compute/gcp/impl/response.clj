(ns compute.gcp.impl.response
  (:require
    [clojure.test :refer :all]
    [provisdom.test.core]
    [compute.gcp.impl.response :as response]))

(deftest http-status->category-gen-test
  (is (spec-check response/http-status->category)))