(ns compute.gcp.impl.descriptor
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
    [clojure.walk :as walk]
    [clojure.java.io :as io]
    [clojure.data.json :as json]
    [compute.gcp.impl.util :as util]))


(defn read-swagger-file
  [path]
  (json/read-str (slurp path) :key-fn util/json-key))


(def http-methods
  #{:get :head :post :put :delete :connect :options :trace :patch})

(defn swagger->descriptor
  [swagger2-spec]
  (let [op->spec (into {}
                       (for [[path method->spec] (:paths swagger2-spec)
                             :let [path-params (:parameters method->spec)]
                             [method spec] method->spec
                             :when (contains? http-methods method)]
                         [(:operationId spec)
                          (cond-> (select-keys spec [:description
                                                     :responses
                                                     :parameters
                                                     :operationId])
                            true (assoc ::http-method method
                                        ::http-path path)
                            path-params
                            (assoc ::path-parameters path-params))]))
        endpoint-map {:schemes   (:schemes swagger2-spec)
                      :host      (:host swagger2-spec)
                      :base-path (:basePath swagger2-spec)}]
    {:compute.api-descriptor/endpoint    endpoint-map
     :compute.api-descriptor/op->spec    op->spec
     :compute.api-descriptor/parameters  (:parameters swagger2-spec)
     :compute.api-descriptor/definitions (:definitions swagger2-spec)}))


(def api-descriptor-resource-path-base-directory
  "computesoftware/api-descriptors")


(defn api-descriptor-resource-path
  [api]
  (format "%s/%s.edn" api-descriptor-resource-path-base-directory (name api)))


(comment
  (.mkdirs (io/file "resources" api-descriptor-resource-path-base-directory))
  (spit (str "resources/" (api-descriptor-resource-path :compute))
        (swagger->descriptor (read-swagger-file "resources/gcp-compute-swagger.json")))
  )





(defn load-descriptor
  [api]
  (edn/read-string (slurp (io/resource (api-descriptor-resource-path api)))))


(defn parse-ref
  [ref-val]
  (let [[_ type location] (str/split ref-val #"\/" 3)]
    [(case type
       "definitions" :compute.api-descriptor/definitions
       "parameters" :compute.api-descriptor/parameters)
     (keyword location)]))


(defn resolve-ref
  [api-descriptor ref-path]
  (get-in api-descriptor (parse-ref ref-path)))


(defn resolve-all-refs
  [form api-descriptor]
  (walk/prewalk
    (fn [form]
      (if-let [ref-val (get form "$ref")]
        (resolve-ref api-descriptor ref-val)
        form))
    form))


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