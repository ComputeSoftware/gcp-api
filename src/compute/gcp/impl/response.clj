(ns compute.gcp.impl.response
  (:require
    [clojure.spec.alpha :as s]
    [clojure.java.io :as io]
    [clojure.data.json :as json]
    [cognitect.anomalies :as anom]
    [compute.gcp.impl.util :as util]))

(defn http-status->category
  "Returns the anomaly category given a HTTP status. `::anomalies/fault` is returned
  if no matching status is found."
  [status]
  (let [status-level (long (/ status 100))]
    (case status-level
      4 (case status
          400 ::anom/incorrect
          401 ::anom/forbidden
          403 ::anom/forbidden
          404 ::anom/not-found
          405 ::anom/unsupported
          409 ::anom/conflict
          ::anom/fault)
      5 (case status
          501 ::anom/unsupported
          503 ::anom/busy
          504 ::anom/unavailable
          505 ::anom/unsupported
          ::anom/fault)
      ::anom/fault)))

(s/fdef http-status->category
  :args (s/cat :status nat-int?)
  :ret ::anom/category)


(defn parse-content-type
  [content-type-str]
  (cond
    (re-find #"application\/json" content-type-str) :json
    :else nil))

(defn normalize-response
  [request {:keys [status body headers] :as response}]
  (with-meta
    (if (::anom/category response)
      response
      (let [status-level (int (/ status 100))
            content-type (parse-content-type (get headers "content-type" ""))
            parsed-body (case content-type
                          :json (json/read (io/reader body) :key-fn util/json-key)
                          (slurp body))]
        (if (= status-level 2)
          parsed-body
          (merge {::anom/category (http-status->category status)}
                 (if (map? parsed-body)
                   parsed-body
                   {:response parsed-body})))))
    {:request  request
     :response response}))

