(ns compute.gcp.impl.descriptor
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
    [clojure.walk :as walk]
    [clojure.set :as sets]
    [clojure.java.io :as io]
    [clojure.data.json :as json]
    [clojure.spec.alpha :as s]
    [compute.gcp.impl.util :as util]
    [compute.gcp.swagger :as swagger]))

(def http-methods
  #{:get :head :post :put :delete :connect :options :trace :patch})

(s/def ::api keyword?)
(s/def ::version string?)
(s/def ::http-method http-methods)
(s/def ::http-path ::swagger/path-endpoint)

(s/def ::url string?)

(s/def :compute.api-descriptor/endpoint
  (s/keys :req-un [::url]))

(s/def ::op-spec
  (s/keys :req [::http-method]
          :req-un [::swagger/responses
                   ::swagger/operationId]
          :opt-un [::swagger/description
                   ::swagger/parameters]))

(s/def :compute.api-descriptor/op->spec
  (s/map-of ::swagger/operationId ::op-spec))

(s/def :compute.api-descriptor/parameters ::swagger/parameters)
(s/def :compute.api-descriptor/definitions ::swagger/definitions)

(s/def ::descriptor
  (s/keys :req [:compute.api-descriptor/endpoint
                :compute.api-descriptor/op->spec
                :compute.api-descriptor/resolve]))

(defn read-swagger-file
  [path]
  (json/read-str (slurp path) :key-fn util/json-key))


(defn openapi-op-lookup
  [paths]
  (into {}
        (for [[path path-item] paths
              [method operation] path-item
              :when (contains? http-methods method)]
          [(:operationId operation)
           (-> (select-keys operation [:operationId
                                       :description
                                       :parameters
                                       :requestBody])
               (assoc ::http-method method
                      ::http-path path))])))

(defn openapi->descriptor
  [openapi-spec]
  (let [{:keys [paths servers]} openapi-spec
        op->spec (openapi-op-lookup paths)
        _ (when (not= 1 (count servers)) (throw (ex-info "Unsupported :servers count" {:servers servers})))
        endpoint-map {::url (:url (first servers))}]
    {:compute.api-descriptor/endpoint endpoint-map
     :compute.api-descriptor/op->spec op->spec
     :compute.api-descriptor/resolve  {:components (:components openapi-spec)}}))


(def api-descriptor-resource-path-base-directory
  "computesoftware/api-descriptors")


(defn api-descriptor-resource-path
  [api api-version]
  (format "%s/%s/%s/api-descriptor.edn"
          api-descriptor-resource-path-base-directory
          (name api)
          api-version))


(comment
  (.mkdirs (io/file "resources" api-descriptor-resource-path-base-directory))
  (spit (str "resources/" (api-descriptor-resource-path :compute))
        (openapi->descriptor (read-swagger-file "resources/gcp-compute-swagger.json")))
  )


(defn parse-ref
  [ref-val]
  (let [[_ & path] (str/split ref-val #"\/")]
    (map keyword path)))


(defn resolve-ref
  [resolve-map ref-path]
  (get-in resolve-map (parse-ref ref-path)))


(defn resolve-all-refs
  [api-descriptor]
  (walk/prewalk
    (fn [form]
      (if-let [ref-val (get form :$ref)]
        (resolve-ref (:compute.api-descriptor/resolve api-descriptor) ref-val)
        form))
    api-descriptor))


(defn load-descriptor
  [api api-version]
  (with-open [rdr (-> (api-descriptor-resource-path api api-version)
                      (io/resource)
                      (io/reader))
              push-rdr (java.io.PushbackReader. rdr)]
    (resolve-all-refs (edn/read push-rdr))))


(defn get-op-descriptor
  [api-descriptor op]
  (let [op->spec (:compute.api-descriptor/op->spec api-descriptor)
        op-info (op->spec op)
        parameters (-> (concat
                         (::path-parameters op-info)
                         (:parameters op-info))
                       (resolve-all-refs api-descriptor))
        responses (:responses op-info)]
    {:description (:description op-info)
     :parameters  parameters
     :method      (::http-method op-info)
     :path        (::http-path op-info)
     :responses   (resolve-all-refs responses api-descriptor)}))


(defn op->param-names
  [swagger]
  (let [ops (keys (::op->spec swagger))]
    (into {}
          (map (fn [op]
                 (let [descriptor (get-op-descriptor swagger op)
                       params (:parameters descriptor)
                       param-names (into []
                                         (mapcat
                                           (fn [param]
                                             (if (= "body" (:in param))
                                               (map name (keys (get-in param [:schema :properties])))
                                               [(:name param)])))
                                         params)]
                   [op param-names])) ops))))


(defn find-duplicated-op-param-names
  [swagger]
  (for [[op params-names] (op->param-names swagger)
        [param-name freq] (frequencies params-names)
        :when (> freq 1)
        :let [descriptor (get-op-descriptor swagger op)
              params (filter (fn [param]
                               (or (= param-name (:name param))
                                   (get-in param [:schema :properties (keyword param-name)])))
                             (:parameters descriptor))]]
    [op {:name       param-name
         :count      freq
         :appears-in (map :in params)}]))