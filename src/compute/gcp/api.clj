(ns compute.gcp.api
  (:require
    [clojure.core.async :as async]
    [clojure.string :as str]
    [compute.gcp.async.api :as async.api]
    [compute.gcp.credentials :as creds]
    [compute.gcp.descriptor :as descriptor]
    [compute.gcp.impl.client :as client-impl]
    [compute.gcp.retry :as retry]))

(def ^:private *default-http-client
  (delay
    ((requiring-resolve 'compute.gcp.java-http-clj/default-http-client))))

(defn default-http-client
  []
  @*default-http-client)

(defn client
  [{:keys [api version http-client credentials-provider backoff retriable?]}]
  (let [descriptor (descriptor/load-descriptor api version)
        credentials-provider (or credentials-provider (creds/get-default))]
    (client-impl/->Client
      {::http-client          (or http-client (default-http-client))
       ::api-descriptor       descriptor
       ::credentials-provider credentials-provider
       ::backoff              (or backoff retry/default-backoff)
       ::retriable?           (or retriable? retry/default-retriable?)})))


;; TODO
(defn doc-str
  [{::descriptor/keys [operation description parameters response] :as doc}]
  (let [required (keep (fn [[parameter {:strs [required]}]]
                         (when required
                           (keyword parameter)))
                   parameters)]
    (when doc
      (str/join "\n"
        (cond-> ["-------------------------"
                 operation
                 ""
                 description]
          parameters
          (into [""
                 "-------------------------"
                 "Request"
                 ""
                 (with-out-str (clojure.pprint/pprint parameters))])
          required
          (into ["Required"
                 ""
                 (with-out-str (clojure.pprint/pprint required))])
          response
          (into ["-------------------------"
                 "Response"
                 ""
                 (with-out-str (clojure.pprint/pprint response))]))))))


(defn doc-data
  [client op]
  (let [op-descriptor (descriptor/get-op-info (::api-descriptor client) op)]
    (-> op-descriptor
      (select-keys [:description :parameters :responses]))))

(defn ops
  [client]
  (::descriptor/op->info (::api-descriptor (client-impl/-get-info client))))

(defn doc
  [client operation]
  (println (or (some-> client ops
                 (get operation)
                 (assoc :compute.gcp.descriptor/operation operation)
                 doc-str)
             (str "No docs for " (name operation)))))


(defn invoke
  [client op-map]
  (async/<!! (async.api/invoke client op-map)))


(defn get-url
  "GCP APIs often return resource URLs. This function simply executes a GET request
  on that url."
  [client url]
  (async/<!! (async.api/get-url client url)))