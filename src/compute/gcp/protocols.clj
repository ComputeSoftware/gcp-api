(ns compute.gcp.protocols)

(defprotocol IHttpClient
  (send-request [http-client request-map callback]))