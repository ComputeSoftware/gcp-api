(ns compute.gcp.impl.discovery-doc
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [compute.gcp.descriptor :as descriptor]))

(comment
  (require 'update-descriptors)
  (def discovery-doc
    (update-descriptors/get-json "https://cloudbilling.googleapis.com/$discovery/rest?version=v1"))
  (def discovery-doc
    (update-descriptors/get-json "https://www.googleapis.com/discovery/v1/apis/compute/v1/rest"))
  (keys discovery-doc)
  (discovery-doc "servicePath")
  (discovery-doc "rootUrl")
  (discovery-doc "servicePath")
  (type (discovery-doc "resources"))
  (discovery-doc "resources")
  (resources->op-lookup (discovery-doc "resources"))
  (discovery-doc "parameters")
  "compute.zoneOperations.delete"

  (for [[_ {:strs [methods]}] (discovery-doc "resources")
        [_ method] methods
        :when (= (method "id") "compute.instances.insert")]
    method)
  )

(defn resources->op-lookup
  [resources]
  (into {}
        (for [[_ {:strs [methods]}] resources
              [_ method] methods]
          [(method "id")
           (cond-> {::descriptor/http-method (keyword (str/lower-case (method "httpMethod")))
                    ::descriptor/path        (or (method "flatPath")
                                                 (method "path"))}
             (method "request")
             (assoc ::descriptor/request (method "request"))
             (method "response")
             (assoc ::descriptor/response (method "response"))
             (method "parameters")
             (assoc ::descriptor/parameters (method "parameters"))
             (method "description")
             (assoc ::descriptor/description (method "description")))])))

(comment
  (get (resources->op-lookup (discovery-doc "resources")) "compute.instances.insert")
  )

(defn discovery-doc-v1->descriptor
  [discovery-doc]
  ;; https://developers.google.com/discovery/v1/reference/apis#resource-representations
  {::descriptor/name        (discovery-doc "name")
   ::descriptor/title       (discovery-doc "title")
   ::descriptor/api-version (discovery-doc "version")
   ::descriptor/revision    (discovery-doc "revision")
   ::descriptor/endpoint    (cond-> {::descriptor/url        (discovery-doc "rootUrl")
                                     ::descriptor/batch-path (discovery-doc "batchPath")}
                              (discovery-doc "servicePath")
                              (assoc ::descriptor/service-path (discovery-doc "servicePath")))
   ::descriptor/parameters  (discovery-doc "parameters")
   ::descriptor/op->info    (resources->op-lookup (discovery-doc "resources"))
   ::descriptor/schemas     (discovery-doc "schemas")})

(comment
  (def descriptor (discovery-doc-v1->descriptor discovery-doc))
  (s/valid? ::descriptor/descriptor descriptor)
  (s/explain-data ::descriptor/descriptor descriptor)
  (get-in descriptor [::descriptor/op->info "compute.zoneOperations.delete"])
  )

(defn discovery-doc->descriptor
  [discovery-doc]
  (let [version (discovery-doc "discoveryVersion")]
    (case (discovery-doc "discoveryVersion")
      "v1" (discovery-doc-v1->descriptor discovery-doc)
      (throw (ex-info (str "Unhandled discovery document version " version)
                      {:version version})))))