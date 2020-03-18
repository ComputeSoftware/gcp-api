(ns compute.gcp.openapi
  (:require
    [clojure.spec.alpha :as s]))

;; https://github.com/OAI/OpenAPI-Specification/blob/771adff68185c563118c4649efeb98767d71c404/versions/3.0.0.md

(s/def ::url string?)
(s/def ::description string?)
(s/def ::summary string?)
(s/def ::name string?)
(s/def ::required boolean?)

(s/def ::default string?)
(s/def ::enum (s/coll-of string? :distinct true))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md#serverVariableObject
(s/def ::variable
  (s/keys :req-un [::enum ::default ::description]))

(s/def ::variable-name string?)

(s/def ::variables
  (s/map-of ::variable-name ::variable))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md#serverObject
(s/def ::server
  (s/keys :req-un [::url ::description]))

(s/def ::servers
  (s/coll-of ::server :min-count 1))

(s/def ::in #{"query" "header" "path" "cookie"})

(s/def ::parameter
  (s/keys :req-un [::name ::in]
          :opt-un [::description
                   ::required]))

(s/def ::parameters
  (s/coll-of ::parameter))

(s/def ::content map?)

(s/def ::requestBody
  (s/keys :req-un [::content]
          :opt-un [::required]))

(s/def ::operationId string?)

;; https://swagger.io/specification/#operationObject
(s/def ::operation
  (s/keys :req-un [::operationId]
          :opt-un [::summary
                   ::description
                   ::parameters
                   ::requestBody]))

(s/def ::get ::operation)
(s/def ::put ::operation)
(s/def ::post ::operation)
(s/def ::delete ::operation)
(s/def ::options ::operation)
(s/def ::head ::operation)
(s/def ::patch ::operation)
(s/def ::trace ::operation)

(s/def ::paths
  (s/keys :opt-un [::get
                   ::put
                   ::post
                   ::delete
                   ::options
                   ::head
                   ::patch
                   ::trace
                   ::parameters]))

(s/def ::openapi-map
  (s/keys :req-un [::servers
                   ]))