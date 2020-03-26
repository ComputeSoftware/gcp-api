(ns compute.gcp.async.api
  (:require
    [compute.gcp.impl.client :as client-impl]))

(defn invoke
  [client op-map]
  (client-impl/invoke client op-map))

(defn get-url
  "GCP APIs often return resource URLs. This function simply executes a GET request
  on that url."
  [client url]
  (client-impl/get-url client url))