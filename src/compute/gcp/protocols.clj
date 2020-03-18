(ns compute.gcp.protocols)

(defprotocol IHttpClient
  (send-request [this request-map callback]))