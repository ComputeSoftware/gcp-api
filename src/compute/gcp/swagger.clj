(ns compute.gcp.swagger
  "Specs for a Swagger document that gcp-api uses. Note these WILL differ from
  the official Swagger docs since gcp-api supports a subset of Swagger and requires
  certain properties to be present that swagger does not."
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [compute.gcp.impl.gen :as genh]))

;; Swagger spec doc:
;; https://github.com/OAI/OpenAPI-Specification/blob/fb059ca461bd17b10a9e3e59879f04485886d356/versions/2.0.md

(defmacro and-
  "Same as s/and except all predicates take the 'raw' value instead of the conformed
  value."
  [& pred-forms]
  (let [pred-forms (map (fn [pred-form]
                          `(s/nonconforming ~pred-form)) pred-forms)]
    `(s/and ~@pred-forms)))

(defn leading-slash?
  [s]
  (str/starts-with? s "/"))

(defn leading-path-str-gen
  []
  (gen/fmap (fn [s] (str "/" s)) (s/gen string?)))

(s/def ::leading-slash-str
  (s/with-gen (s/and string? leading-slash?) leading-path-str-gen))

(s/def ::data-type-format #{"int32" "int64" "float" "double" "byte" "binary" "date" "date-time" "password"})

(s/def ::json-schema-primitive-type
  #{"array" "boolean" "integer" "number" "null" "object" "string"})

(s/def ::header-object any?)

(s/def ::spec qualified-keyword?)

(s/def ::response-code (s/or :number (s/int-in 100 600) :default #{:default}))

(s/def ::name string?)
(s/def ::url string?)
(s/def ::email string?)

(s/def ::title string?)
(s/def ::description string?)
(s/def ::termsOfService string?)

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#contact-object
(s/def ::contact (s/keys :opt-un [::name ::url ::email]))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#license-object
(s/def ::license (s/keys :req-un [::name] :opt-un [::url]))

(s/def ::consumes (s/coll-of string? :gen-max 3))
(s/def ::produces (s/coll-of string? :gen-max 3))

(s/def ::version string?)

(s/def :compute.gcp.swagger.security-definition/type #{"basic" "apiKey" "oauth2"})
(s/def :compute.gcp.swagger.security-definition/in #{"query" "header"})
(s/def ::flow #{"implicit" "password" "application" "accessCode"})
(s/def ::authorizationUrl string?)
(s/def ::tokenUrl string?)
(s/def ::scopes (s/map-of string? string?))
(s/def ::security-definition
  (s/keys :req-un [:compute.gcp.swagger.security-definition/type]
          :opt-un [::name
                   ::description
                   ::flow
                   ::authorizationUrl
                   ::tokenUrl
                   ::scopes
                   :compute.gcp.swagger.security-definition/in]))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#info-object
(s/def ::info
  (s/keys :req-un [::title
                   ::version]
          :opt-un [::description
                   ::termsOfService
                   ::contact
                   ::license]))

(s/def ::collectionFormat #{"csv" "ssv" "tsv" "pipes"})

;; TODO: write better spec & gen for $ref
;; Should match the JSON pointer spec: https://tools.ietf.org/html/rfc6901
(s/def ::$ref string?)

(s/def ::format string?)

(s/def ::number
  (s/or :int int? :double (s/double-in :infinite? false :NaN? false)))
(s/def ::maximum ::number)
(s/def ::exclusiveMaximum boolean?)
(s/def ::minimum ::number)
(s/def ::exclusiveMinimum boolean?)

(s/def ::maxLength nat-int?)
(s/def ::minLength nat-int?)

(s/def ::pattern string?)

(s/def ::maxItems nat-int?)
(s/def ::minItems nat-int?)

(s/def ::uniqueItems boolean?)

;; https://tools.ietf.org/html/draft-fge-json-schema-validation-00#section-5.5.1
(s/def ::enum (s/coll-of (s/or :str string?
                               :num ::number
                               :nil nil?
                               :boolean boolean?)
                         :distinct true))

(s/def ::multipleOf nat-int?)

;; https://tools.ietf.org/html/draft-fge-json-schema-validation-00#section-5.4.1
(s/def ::maxProperties nat-int?)
;; https://tools.ietf.org/html/draft-fge-json-schema-validation-00#section-5.4.2
(s/def ::minProperties nat-int?)

;; https://tools.ietf.org/html/draft-fge-json-schema-validation-00#section-5.4.3
(s/def ::required (s/coll-of string? :min-count 1 :distinct true))

(s/def ::in #{"query" "header" "path" "formData" "body"})
(s/def :compute.gcp.swagger.parameter/required boolean?)

(s/def :compute.gcp.swagger.parameter/type
  #{"string" "number" "integer" "boolean" "array" "file"})

(s/def :compute.gcp.swagger.parameter/format ::data-type-format)
(s/def ::allowEmptyValue boolean?)

(defmulti parameter-spec :in)

(defmethod parameter-spec :default
  [_]
  (s/keys :req-un [:compute.gcp.swagger.parameter/type]
          :opt-un [:compute.gcp.swagger.parameter/format
                   ::allowEmptyValue
                   ::collectionFormat
                   ::maximum
                   ::exclusiveMaximum
                   ::minimum
                   ::exclusiveMinimum
                   ::maxLength
                   ::minLength
                   ::pattern
                   ::maxItems
                   ::minItems
                   ::uniqueItems
                   ::enum
                   ::multipleOf]))

(defmethod parameter-spec "body"
  [_]
  (s/keys :req-un [::schema]))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#parameter-object
(s/def ::parameter
  (s/merge
    (s/keys :req-un [::name ::in]
            :opt-un [::description
                     :compute.gcp.swagger.parameter/required])
    (s/multi-spec parameter-spec :in)))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#reference-object
(s/def ::reference
  (s/keys :req-un [::$ref]))

(s/def :compute.gcp.swagger.operation/tags (s/coll-of string? :gen-max 3))
(s/def ::summary string?)
;; TODO: does this need to be more restrictive?
(s/def ::operationId string?)

(s/def :compute.gcp.swagger.schema/type ::json-schema-primitive-type)

(s/def ::properties (s/map-of string? ::schema))

(s/def ::additionalProperties
  (s/or :bool boolean? :schema ::schema))

(s/def ::schema
  (s/keys :opt-un [::$ref
                   :compute.gcp.swagger.schema/type
                   ::format
                   ::title
                   ::description
                   ::multipleOf
                   ::maximum
                   ::exclusiveMaximum
                   ::minimum
                   ::exclusiveMinimum
                   ::maxLength
                   ::minLength
                   ::pattern
                   ::maxItems
                   ::minItems
                   ::uniqueItems
                   ::maxProperties
                   ::required
                   ::enum
                   ::items
                   ::properties
                   ::additionalProperties]))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#definitionsObject
(s/def ::definitions
  (s/map-of string? ::schema))

(s/def :compute.gcp.swagger.header/type #{"string" "number" "integer" "boolean" "array"})

(s/def ::items
  (s/keys))

(defmulti header-type-spec :type)
(defmethod header-type-spec :default [_] (s/keys))
(defmethod header-type-spec "array"
  [_]
  (s/keys :req-un [::items]))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#header-object
(s/def ::header
  (s/merge
    (s/keys :req-un [:compute.gcp.swagger.header/type]
            :opt-un [::description
                     ::format
                     ::maximum
                     ::exclusiveMaximum
                     ::minimum
                     ::exclusiveMinimum
                     ::maxLength
                     ::minLength
                     ::pattern
                     ::maxItems
                     ::minItems
                     ::uniqueItems
                     ::enum
                     ::multipleOf])
    (s/multi-spec header-type-spec :type)))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#headers-object
(s/def ::headers
  (s/map-of string? ::header))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#exampleObject
(s/def ::examples (s/map-of string? any?))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#response-object
(s/def ::response
  (s/keys :req-un [::description
                   ::schema
                   ::headers
                   ::examples]))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#responsesObject
(s/def ::responses
  (s/coll-of ::response :min-count 1 :gen-max 10))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#operation-object
(s/def ::operation
  (s/keys :req-un [::responses
                   ::operationId                            ; opt in official
                   ]
          :opt-un [:compute.gcp.swagger.operation/tags
                   ::summary
                   ::description
                   ::externalDocs
                   ::produces
                   ::consumes
                   ::parameters]))


(s/def ::get ::operation)
(s/def ::put ::operation)
(s/def ::post ::operation)
(s/def ::delete ::operation)
(s/def ::options ::operation)
(s/def ::head ::operation)
(s/def ::patch ::operation)

(defn find-body-parameters
  [parameters]
  (filter (fn [{:keys [in]}] (= in "body")) parameters))

(defn one-body-param?
  [parameters]
  (< (count (find-body-parameters parameters)) 2))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#parametersDefinitionsObject
;; TODO: There can be one "body" parameter at most.
(s/def ::parameters
  (s/with-gen
    (s/coll-of (s/or :parameter ::parameter :reference ::reference))
    #(genh/vector-distinct-by
       (fn [{:keys [in]}]
         (= in "body"))
       (s/gen
         (s/or :parameter ::parameter :reference ::reference))
       {:max-elements 10})))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#patterned-fields
(s/def ::path-endpoint ::leading-slash-str)

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#path-item-object
(s/def ::path-item
  (s/keys :opt-un [::$ref
                   ::get
                   ::put
                   ::post
                   ::delete
                   ::options
                   ::head
                   ::patch]))

;; Does NOT support path templating
(s/def ::host string?)

(s/def ::basePath ::leading-slash-str)
(def schemes #{"http" "https" #_"ws" #_"wss"})
(s/def ::schemes (s/coll-of schemes))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#pathsObject
(s/def ::paths (s/map-of ::path-endpoint ::path-item :gen-max 5))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#securityDefinitionsObject
(s/def ::securityDefinitions
  (s/map-of string? ::security-definition))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#securityRequirementObject
(s/def ::security (s/coll-of (s/map-of string? (s/coll-of string?))))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#tag-object
(s/def ::tag
  (s/keys :req-un [::name]
          :opt-un [::description ::externalDocs]))

(s/def ::tags (s/coll-of ::tag))

(s/def ::swagger #{"2.0"})

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#externalDocumentationObject
(s/def ::externalDocs
  (s/keys :req-un [::url]
          :opt-un [::description]))

;; https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#fixed-fields
(s/def ::swagger-definition
  (s/keys :req-un [::swagger
                   ::info
                   ::paths
                   ::host                                   ;; opt in official
                   ]
          :opt-un [::basePath
                   ::schemes
                   ::consumes
                   ::produces
                   ::definitions
                   ::parameters
                   ::responses
                   ::securityDefinitions
                   ::security
                   ::tags
                   ::externalDocs]))