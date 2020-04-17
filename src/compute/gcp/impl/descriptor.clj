(ns compute.gcp.impl.descriptor
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
    [clojure.walk :as walk]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]))

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