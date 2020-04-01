(ns compute.gcp.impl.client
  (:require
    [clojure.core.async :as async]
    [cognitect.anomalies :as anom]
    [compute.gcp.impl.request :as request]
    [compute.gcp.impl.response :as response]
    [compute.gcp.protocols :as proto]
    [compute.gcp.retry :as retry]))

(defn send-request-async
  [http-client request]
  (let [out-ch (async/promise-chan)]
    (proto/send-request
      http-client
      request
      #(async/put! out-ch (response/normalize-response %)))
    out-ch))

(defn send-request!
  [client request]
  (let [out-ch (async/promise-chan)]
    (if (::anom/category request)
      (async/put! out-ch request)
      (retry/with-retry
        #(send-request-async (:compute.gcp.api/http-client client) request)
        out-ch
        (:compute.gcp.api/retriable? client)
        (:compute.gcp.api/backoff client)))
    out-ch))

(defn invoke
  [client op-map]
  (send-request! client (request/op-request-map client op-map)))

(defn get-url
  "GCP APIs often return resource URLs. This function simply executes a GET request
  on that url."
  [client url]
  (let [req-map (request/url-request-map client url)]
    (send-request! client req-map)))