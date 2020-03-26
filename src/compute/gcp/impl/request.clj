(ns compute.gcp.impl.request
  (:require
    [clojure.string :as str]
    [clojure.data.json :as json]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [compute.gcp.impl.descriptor :as descriptor]
    [compute.gcp.credentials :as creds]))


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


(defn with-request-parameters
  "Returns request with the op's op-parameters from request-parameters added
  into the appropriate locations in the request map."
  [request parameters request-body request-parameters]
  (let [;; TODO: handle multiple content-types?
        body-keys (keys (get-in request-body [:content "application/json" :schema :properties]))
        ;; TODO: handle parameters with the same name
        name->param-info (into {}
                               (map (fn [param-info]
                                      [(keyword (:name param-info)) param-info]))
                               parameters)
        parameter-keys (keys name->param-info)]
    (reduce-kv
      (fn [req-map param-name param-value]
        (let [param-info (name->param-info param-name)
              param-name (:name param-info)]
          (case (:in param-info)
            "query" (with-query-parameter req-map param-name param-value)
            "path" (with-path-parameter req-map param-name param-value)
            "header" (with-header-parameter req-map param-name param-value)
            "formData" (with-form-parameter req-map param-name param-value))))
      (cond-> request
        ;; TODO: Could remove duplicated req-param keys from the body here
        body-keys (assoc :body (json/write-str (select-keys request-parameters body-keys))))
      (select-keys request-parameters parameter-keys))))

(defn build-request-map
  [endpoint op-descriptor op-map]
  (let [{:keys [request timeout]} op-map
        base-req (cond-> {:method (::descriptor/http-method op-descriptor)
                          :uri    (str (::descriptor/url endpoint)
                                       (::descriptor/http-path op-descriptor))}
                   timeout (assoc :timeout timeout))]
    (with-request-parameters
      base-req
      (:parameters op-descriptor)
      (:requestBody op-descriptor)
      request)))

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

(defn get-op-descriptor
  [client op]
  (get-in client [:compute.gcp.api/api-descriptor :compute.api-descriptor/op->spec op]))

(defn op-request-map
  [client op-map]
  (if-let [op-descriptor (get-op-descriptor client (:op op-map))]
    (let [request (try
                    (build-request-map
                      (get-in client [:compute.gcp.api/api-descriptor :compute.api-descriptor/endpoint])
                      op-descriptor
                      op-map)
                    (catch Exception ex
                      {::anom/category ::anom/fault
                       ::anom/message  (str "Exception while creating the HTTP request map. " (.getMessage ex))
                       :ex             ex
                       :op-map         op-map}))]
      (if (::anom/category request)
        request
        (let [creds-map (creds/fetch (:compute.gcp.api/credentials-provider client))]
          (if (::anom/category creds-map)
            creds-map
            (with-auth request (::creds/access-token creds-map))))))
    {::anom/category ::anom/incorrect
     ::anom/message  (str "Unknown operation " (:op op-map) ".")
     :op             (:op op-map)}))

(defn url-request-map
  [client url]
  (let [creds-map (creds/fetch (:compute.gcp.api/credentials-provider client))
        request {:method :get
                 :uri    url}]
    (if (::anom/category creds-map)
      creds-map
      (with-auth request (::creds/access-token creds-map)))))