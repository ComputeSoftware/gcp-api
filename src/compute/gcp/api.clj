(ns compute.gcp.api
  (:require
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [aleph.http :as http]
    [manifold.deferred :as deferred]
    [compute.gcp.impl.descriptor :as descriptors]
    [compute.gcp.impl.http :as http-impl]))

(defn client
  [{:keys [api credentials-provider]}]
  (let [descriptor (descriptors/load-descriptor api)]
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
  (-> (get-in client [::api-descriptor :compute.api-descriptor/op->spec op])
      (select-keys [:description :parameters :responses])))


(defn ops
  [client]
  (keys (get-in client [::api-descriptor :compute.api-descriptor/op->spec])))


(defn invoke
  [client op-map]
  (let [req-map (http-impl/op-request-map client op-map)]
    (if (::anom/category req-map)
      (-> (deferred/deferred)
          (deferred/success! req-map))
      (deferred/chain
        (http/request req-map)
        (fn [http-response]
          (http-impl/normalize-response http-response))))))