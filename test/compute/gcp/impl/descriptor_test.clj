(ns compute.gcp.impl.descriptor-test
  (:require
    [clojure.test :refer :all]
    [provisdom.test.core :as t :refer [spec-check]]
    [compute.gcp.descriptor :as descriptor]))

(deftest api-descriptor-resource-path-test
  (is (= "computesoftware/api-descriptors/compute/v1/api-descriptor.edn"
         (descriptor/api-descriptor-resource-path :compute "v1"))))