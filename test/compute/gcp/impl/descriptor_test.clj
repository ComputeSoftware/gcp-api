(ns compute.gcp.impl.descriptor-test
  (:require
    [clojure.test :refer :all]
    [provisdom.test.core :as t :refer [spec-check]]
    [compute.gcp.descriptor :as descriptor]))

(deftest api-descriptor-resource-path-test
  (is (= "computesoftware/api-descriptors/compute/v1/api-descriptor.edn"
         (descriptor/api-descriptor-resource-path :compute "v1"))))


(deftest recursive-schema
  (is (= {"nonref" {"type" "object"}

          "TableSchema"
          {"type"       "object"
           "properties" {"fields"  {"items" {"type" "object"
                                             "properties"
                                             {"fields"
                                              {"items"
                                               {"$ref" "TableFieldSchema"
                                                :compute.gcp.descriptor/recursive?
                                                true}
                                               "type" "array"}}
                                             "id"   "TableFieldSchema"}
                                    "type"  "array"}
                         "fields2" {"items" {"type" "object"}, "type" "array"}
                         "fields3" {"items" {"type" "object"}, "type" "array"}}
           "id"         "TableSchema"}
          "TableFieldSchema"
          {"type"       "object"
           "properties" {"fields" {"items" {"type"       "object"
                                            "properties" {"fields" {"items" {"$ref" "TableFieldSchema"
                                                                             :compute.gcp.descriptor/recursive?
                                                                             true}
                                                                    "type"  "array"}},
                                            "id"         "TableFieldSchema"}
                                   "type"  "array"}}
           "id"         "TableFieldSchema"}}
        (-> (descriptor/resolve-descriptor-refs
              {:compute.gcp.descriptor/schemas
               {"nonref"           {"type" "object"}
                "TableSchema"      {"type"       "object",
                                    "properties" {"fields"  {"items" {"$ref" "TableFieldSchema"},
                                                             "type"  "array"}
                                                  "fields2" {"items" {"$ref" "nonref"},
                                                             "type"  "array"}
                                                  "fields3" {"items" {"$ref" "nonref"},
                                                             "type"  "array"}},
                                    "id"         "TableSchema"}
                "TableFieldSchema" {"type"       "object",
                                    "properties" {"fields" {"items" {"$ref" "TableFieldSchema"},
                                                            "type"  "array"}}
                                    "id"         "TableFieldSchema"}}})
          :compute.gcp.descriptor/schemas))))

