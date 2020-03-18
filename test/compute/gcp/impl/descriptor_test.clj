(ns compute.gcp.impl.descriptor-test
  (:require
    [clojure.test :refer :all]
    [provisdom.test.core :as t :refer [spec-check]]
    [compute.gcp.impl.descriptor :as d]))

(deftest api-descriptor-resource-path-test
  (is (= "computesoftware/api-descriptors/compute/v1/api-descriptor.edn"
         (d/api-descriptor-resource-path :compute "v1"))))

(deftest openapi-op-lookup-gen-test
  (is (spec-check `d/openapi-op-lookup
                  {:recursion-limit  0
                   :num-tests        100
                   :coll-error-limit 1})))