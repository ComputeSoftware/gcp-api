(ns compute.gcp.impl.client
  (:require
    [clojure.core.async :as async]
    [cognitect.anomalies :as anom]
    [compute.gcp.impl.request :as request]
    [compute.gcp.impl.response :as response]
    [compute.gcp.protocols :as proto]
    [compute.gcp.retry :as retry]))

(defprotocol ClientSPI
  (-get-info [_] "Intended for internal use only"))

(deftype Client [client-info]
  ClientSPI
  (-get-info [_] client-info))

(defn send-request-async
  [http-client request]
  (let [out-ch (async/promise-chan)]
    (proto/send-request
      http-client
      request
      #(async/put! out-ch (response/normalize-response request %)))
    out-ch))

(defn send-request!
  [client request]
  (let [out-ch (async/promise-chan)
        cinfo (-get-info client)]
    (if (::anom/category request)
      (async/put! out-ch request)
      (retry/with-retry
        #(send-request-async (:compute.gcp.api/http-client cinfo) request)
        out-ch
        (:compute.gcp.api/retriable? cinfo)
        (:compute.gcp.api/backoff cinfo)))
    out-ch))

(defn invoke
  [client op-map]
  (send-request! client (request/op-request-map (-get-info client) op-map)))

(defn get-url
  "GCP APIs often return resource URLs. This function simply executes a GET request
  on that url."
  [client url]
  (let [req-map (request/url-request-map (-get-info client) url)]
    (send-request! client req-map)))