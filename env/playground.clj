(ns playground
  (:require
    [compute.gcp.api :as gcp-api]))

(comment
  (def client (gcp-api/client {:api     :compute
                               :version "v1"}))
  (gcp-api/invoke
    client
    {:op      "compute.instances.list"
     :request {:project ""
               :zone    "us-central1-c"}})
  )