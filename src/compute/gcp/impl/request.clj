(ns compute.gcp.impl.request
  (:require
    [clojure.string :as str]
    [clojure.data.json :as json]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [compute.gcp.descriptor :as descriptor]
    [compute.gcp.credentials :as creds])
  (:import (com.google.api.client.http UriTemplate)))


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
  (update request :uri (fn [uri] (str/replace (or uri "") (format "{%s}" (name param-name)) param-val))))


(defn with-header-parameter
  [request param-name param-val]
  (assoc-in request [:headers (name param-name)] param-val))


(defn with-form-parameter
  [request param-name param-val]
  (assoc-in request [:form-params (name param-name)] param-val))


(defn with-auth
  [request access-token]
  (assoc-in request [:headers "Authorization"] (str "Bearer " access-token)))

(defn expand-uri-template
  [uri-template param-name->param-val]
  (UriTemplate/expand "" uri-template param-name->param-val false))

(defn with-request-parameters
  "Returns request with the op's op-parameters from request-parameters added
  into the appropriate locations in the request map."
  [request parameters body-schema parameter-kvs]
  (let [body-keys (map keyword (keys (get body-schema "properties")))
        path-params (into {}
                          (keep (fn [[k v]]
                                  (when (= "path" (get-in parameters [(name k) "location"]))
                                    [(name k) v])))
                          parameter-kvs)
        query-header-form-data-params
        (into []
              (comp
                (map keyword)
                (filter (fn [k]
                          (not (path-params (name k))))))
              (keys parameters))]
    (reduce-kv
      (fn [req-map param-name param-value]
        (let [param-name (name param-name)
              param-schema (get parameters param-name)]
          (case (param-schema "location")
            "query" (with-query-parameter req-map param-name param-value)
            "header" (with-header-parameter req-map param-name param-value)
            "formData" (with-form-parameter req-map param-name param-value))))
      (cond-> request
        true
        (update :uri (fn [uri-template]
                       (expand-uri-template uri-template path-params)))
        ;; TODO: Could remove duplicated req-param keys from the body here
        (not-empty body-keys)
        (assoc :body (json/write-str (select-keys parameter-kvs body-keys))))
      (select-keys parameter-kvs query-header-form-data-params))))

(defn build-request-map
  [endpoint op-info op-map]
  (let [{:keys [request timeout as]} op-map
        base-req (cond-> {:method (::descriptor/http-method op-info)
                          :uri    (str (::descriptor/url endpoint)
                                       (::descriptor/service-path endpoint)
                                       (::descriptor/path op-info))}
                   timeout (assoc :timeout timeout)
                   as (assoc :as as))]
    (with-request-parameters
      base-req
      (::descriptor/parameters op-info)
      (::descriptor/request op-info)
      request)))

(comment
  (def descriptor (descriptor/load-descriptor "compute" "v1"))
  (def op-info (get-in descriptor [::descriptor/op->info "compute.instances.list"]))
  (::descriptor/parameters op-info)
  (::descriptor/request op-info)
  (build-request-map
    (::descriptor/endpoint descriptor)
    op-info
    {:request {:project    "myprj"
               :zone       "zone1"
               :maxResults 1
               :pageToken  "asd"}})
  )

(defn get-op-descriptor
  [client op]
  (descriptor/get-op-info (:compute.gcp.api/api-descriptor client) op))

(defn op-request-map
  [cinfo op-map]
  (if-let [op-descriptor (get-op-descriptor cinfo (:op op-map))]
    (let [request (try
                    (build-request-map
                      (get-in cinfo [:compute.gcp.api/api-descriptor ::descriptor/endpoint])
                      op-descriptor
                      op-map)
                    (catch Exception ex
                      {::anom/category ::anom/fault
                       ::anom/message  (str "Exception while creating the HTTP request map. " (.getMessage ex))
                       :ex             ex
                       :op-map         op-map}))]
      (if (::anom/category request)
        request
        (let [creds-map (try
                          (creds/fetch (:compute.gcp.api/credentials-provider cinfo))
                          (catch Throwable ex
                            {::anom/category ::anom/fault
                             ::anom/message  "Failed to fetch creds."
                             :ex             ex}))]
          (if (::anom/category creds-map)
            creds-map
            (with-auth request (::creds/access-token creds-map))))))
    {::anom/category ::anom/incorrect
     ::anom/message  (str "Unknown operation " (:op op-map) ".")
     :op             (:op op-map)}))

(defn url-request-map
  [cinfo url]
  (let [creds-map (creds/fetch (:compute.gcp.api/credentials-provider cinfo))
        request {:method :get
                 :uri    url}]
    (if (::anom/category creds-map)
      creds-map
      (with-auth request (::creds/access-token creds-map)))))