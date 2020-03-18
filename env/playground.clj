(ns playground
  (:require
    [compute.gcp.api :as gcp-api]
    [compute.gcp.credentials :as creds]
    [compute.gcp.impl.request :as req]))

(def creds
  (creds/service-account-creds
    {:file   "creds.json"
     :scopes ["https://www.googleapis.com/auth/cloud-platform"]}))

(comment
  (def client (gcp-api/client {:api                  :compute
                               :version              "v1"
                               :credentials-provider creds}))
  (gcp-api/invoke
    client
    {:op      "compute.instances.list"
     :request {:project ""
               :zone    "us-central1-c"}})

  (req/get-op-descriptor client "compute.instances.list")
  )