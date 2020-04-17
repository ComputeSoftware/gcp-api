(ns compute.gcp.java-http-clj
  (:require
    [cognitect.anomalies :as anom]
    [java-http-clj.core :as http]
    [compute.gcp.protocols :as proto])
  (:import (java.net.http HttpConnectTimeoutException HttpTimeoutException)
           (java.time Duration)))

(defn exception-as-anomaly
  [ex]
  (with-meta
    (condp instance? ex
      HttpConnectTimeoutException
      {::anom/category ::anom/busy
       ::anom/message  (.getMessage ex)}
      HttpTimeoutException
      {::anom/category ::anom/busy
       ::anom/message  (.getMessage ex)}
      {::anom/category ::anom/fault
       ::anom/message  (.getMessage ex)})
    {:ex ex}))

(defrecord HttpClient [http-client]
  proto/IHttpClient
  (send-request [_ req-map callback]
    (try
      (http/send-async
        req-map
        {:client http-client
         :as     :input-stream}
        (fn [resp]
          (callback resp))
        (fn [ex]
          (callback (exception-as-anomaly ex))))
      (catch Throwable ex
        (callback (exception-as-anomaly ex))))
    nil))

(def default-client-opts
  {:connect-timeout (Duration/ofSeconds 30)})

(defn new-client
  "Returns a new HttpClient with the given client-opts.

  Sets :connect-timeout to 30 seconds by default."
  [client-opts]
  (map->HttpClient
    {:http-client
     (http/build-client
       (merge default-client-opts client-opts))}))

(def ^:private *default-http-client
  (delay
    (new-client {})))

(defn default-http-client
  []
  @*default-http-client)