(ns compute.gcp.impl.http
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [cognitect.anomalies :as anom]
    [clojure.data.json :as json]
    [compute.gcp.impl.descriptor :as descriptors]
    [compute.gcp.credentials :as creds]
    [compute.gcp.impl.util :as util]))


(def curly-brackets-regex #"\{(.*?)\}")

(defn path-template-var-names
  [path-template]
  (map (fn [[_ var-name]] var-name) (re-seq curly-brackets-regex path-template)))

(defn resolve-path-template
  [path-template var->val]
  (reduce
    (fn [path var-name]
      (str/replace path
                   (format "{%s}" var-name)
                   (get var->val (keyword var-name) "null")))
    path-template (path-template-var-names path-template)))


(defn with-query-parameter
  [request param-name param-val]
  (assoc-in request [:query-params param-name] param-val))


(defn with-path-parameter
  [request param-name param-val]
  (update request :url (fn [url] (str/replace url (format "{%s}" param-name) param-val))))


(defn with-header-parameter
  [request param-name param-val]
  (assoc-in request [:headers (name param-name)] param-val))


(defn with-form-parameter
  [request param-name param-val]
  (assoc-in request [:form-params (name param-name)] param-val))


(defn with-auth
  [request access-token]
  (assoc-in request [:headers "Authorization"] (str "Bearer " access-token)))


(defn with-request-parameters
  [request parameters request-parameters]
  (let [body-keys (-> (filter #(= "body" (:in %)) parameters)
                      (first)
                      (get-in [:schema :properties])
                      (keys))

        not-body-param-infos (filter #(not= "body" (:in %)) parameters)
        not-body-param->info (into {}
                                   (map (fn [param-info]
                                          [(keyword (:name param-info)) param-info]))
                                   not-body-param-infos)
        not-body-param-keys (keys not-body-param->info)]
    (reduce-kv
      (fn [req-map param-name param-value]
        (let [param-info (not-body-param->info param-name)
              param-name (:name param-info)]
          (case (:in param-info)
            "query" (with-query-parameter req-map param-name param-value)
            "path" (with-path-parameter req-map param-name param-value)
            "header" (with-header-parameter req-map param-name param-value)
            "formData" (with-form-parameter req-map param-name param-value))))
      (cond-> request
        ;; TODO: Could remove duplicated req-param keys from the body here
        body-keys (assoc :body (json/write-str (select-keys request-parameters body-keys))))
      (select-keys request-parameters not-body-param-keys))))

(defn select-scheme
  [schemes]
  (let [priorities {"https" 0
                    "http"  1}
        supported-schemes (set (keys priorities))
        sorted-schemes (sort-by #(get priorities 10000)
                                (filter #(contains? supported-schemes %)
                                        schemes))]
    (first sorted-schemes)))

(defn build-request-map
  [endpoint op-descriptor request-map]
  (let [op-url-path (::descriptors/http-path op-descriptor)
        url (format "%s://%s%s%s"
                    (select-scheme (:schemes endpoint))
                    (:host endpoint)
                    (:base-path endpoint)
                    op-url-path)
        base-req {:method (::descriptors/http-method op-descriptor)
                  :url    url}]
    (with-request-parameters base-req (:parameters op-descriptor) request-map)))

(comment
  (build-request-map
    {:scheme    "https"
     :host      "compute.googleapis.com"
     :base-path "/compute/v1/projects"}
    (get-op-descriptor compute-spec "compute.instances.list")
    {:project    "myprj"
     :zone       "zone1"
     :maxResults 1
     :pageToken  "asd"})
  )

(defn op-request-map
  [client op-map]
  (if-let [op-descriptor (get-in client [:compute.gcp.api/api-descriptor :compute.api-descriptor/op->spec (:op op-map)])]
    (let [request (build-request-map
                    (get-in client [:compute.gcp.api/api-descriptor :compute.api-descriptor/endpoint])
                    op-descriptor
                    (:request op-map))
          creds-map (creds/fetch (:compute.gcp.api/credentials-provider client))]
      (if (::anom/category creds-map)
        creds-map
        (with-auth request (::creds/access-token creds-map))))
    {::anom/category ::anom/incorrect
     ::anom/message  (str "Unknown operation " (:op op-map) ".")
     :op             (:op op-map)}))

(defn http-status->category
  "Returns the anomaly category given a HTTP status. `::anomalies/fault` is returned
  if no matching status is found."
  [status]
  (let [status-level (int (/ status 100))]
    (case status-level
      4 (case status
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


(defn parse-content-type
  [content-type-str]
  (cond
    (re-find #"application\/json" content-type-str) :json
    :else nil))

(defn normalize-response
  [{:keys [status body headers] :as response}]
  (let [status-level (int (/ status 100))
        content-type (parse-content-type (get headers "content-type" ""))
        parsed-body (case content-type
                      :json (json/read (io/reader body) :key-fn util/json-key)
                      (slurp body))]
    (with-meta
      (if (= status-level 2)
        parsed-body
        (merge {::anom/category (http-status->category status)}
               parsed-body))
      (select-keys response [:status :headers]))))