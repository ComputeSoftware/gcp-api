(ns compute.gcp.api
  (:require
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [aleph.http :as http]
    [manifold.deferred :as deferred]
    [compute.gcp.impl.descriptor :as descriptors]
    [compute.gcp.impl.http :as http-impl]))

(defn client
  [{:keys [api version credentials-provider]}]
  (let [descriptor (descriptors/load-descriptor api version)]
    {::api-descriptor       descriptor
     ::credentials-provider credentials-provider}))


;; TODO
(defn doc-str
  [{:keys [documentation request required response refs] :as doc}]
  (when doc
    (str/join "\n"
              (cond-> ["-------------------------"
                       (:name doc)
                       ""
                       documentation]
                request
                (into [""
                       "-------------------------"
                       "Request"
                       ""
                       (with-out-str (clojure.pprint/pprint request))])
                required
                (into ["Required"
                       ""
                       (with-out-str (clojure.pprint/pprint required))])
                response
                (into ["-------------------------"
                       "Response"
                       ""
                       (with-out-str (clojure.pprint/pprint response))])
                refs
                (into ["-------------------------"
                       "Given"
                       ""
                       (with-out-str (clojure.pprint/pprint refs))])))))


(defn doc-data
  [client op]
  (let [op-descriptor (descriptors/get-op-descriptor (::api-descriptor client) op)]
    (-> op-descriptor
        (select-keys [:description :parameters :responses]))))


(defn ops
  [client]
  (keys (get-in client [::api-descriptor :compute.api-descriptor/op->spec])))


(defn invoke
  [client op-map]
  (let [req-map (http-impl/op-request-map client op-map)]
    (http-impl/send-request! req-map)))


(defn get-url
  "GCP APIs often return resource URLs. This function simply executes a GET request
  on that url."
  [client url]
  (let [req-map (http-impl/url-request-map client url)]
    (http-impl/send-request! req-map)))