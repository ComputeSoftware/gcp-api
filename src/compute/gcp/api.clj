(ns compute.gcp.api
  (:require
    [clojure.string :as str]
    [clojure.core.async :as async]
    [compute.gcp.impl.descriptor :as descriptors]
    [compute.gcp.impl.client :as client-impl]
    [compute.gcp.async.api :as async.api]))

(def ^:private *default-http-client
  (delay
    ((requiring-resolve 'compute.gcp.java-http-clj/default-http-client))))

(defn default-http-client
  []
  @*default-http-client)

(defn client
  [{:keys [api version http-client credentials-provider]}]
  (let [descriptor (descriptors/load-descriptor api version)]
    {::http-client          (or http-client (default-http-client))
     ::api-descriptor       descriptor
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
  (async/<!! (async.api/invoke client op-map)))


(defn get-url
  "GCP APIs often return resource URLs. This function simply executes a GET request
  on that url."
  [client url]
  (async/<!! (async.api/get-url client url)))