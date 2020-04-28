(ns playground
  (:require
    [compute.gcp.api :as gcp-api]
    [compute.gcp.impl.request :as request]))

(comment
  (def client (gcp-api/client {:api     :compute
                               :version "v1"}))
  (gcp-api/invoke
    client
    {:op      "compute.instances.list"
     :request {:project ""
               :zone    "us-central1-c"}})
  )

(comment
  (def client (gcp-api/client {:api     :cloudbilling
                               :version "v1"}))
  (gcp-api/invoke
    client
    {:op      "cloudbilling.services.skus.list"
     :request {:parent "services/9662-B51E-5089"}
     :as      :input-stream})

  (request/op-request-map
    client
    {:op      "cloudbilling.services.skus.list"
     :request {:parent "services/9662-B51E-5089"}})

  ((:compute.gcp.descriptor/op->info (::gcp-api/api-descriptor client))
   "cloudbilling.services.skus.list")
  )