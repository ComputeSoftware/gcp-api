(ns compute.gcp.descriptor
  (:require
    [clojure.spec.alpha :as s]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [clojure.walk :as walk]))

;; TODO
(s/def ::json-schema map?)

(s/def ::name string?)
(s/def ::title string?)
(s/def ::api-version string?)
(s/def ::revision string?)

(s/def ::url string?)
(s/def ::service-path string?)
(s/def ::batch-path string?)
(s/def ::endpoint
  (s/keys :req [::url]
          :opt [::batch-path
                ::service-path]))


;; TODO:
(s/def ::parameter map?)

(s/def ::parameter-name string?)
(s/def ::parameters
  (s/map-of ::parameter-name ::parameter))

(s/def ::http-method #{:get :put :post :delete :options :head :patch :trace})
(s/def ::path string?)
(s/def ::request ::json-schema)
(s/def ::response ::json-schema)
(s/def ::parameters map?)
(s/def ::description string?)
(s/def ::op-info
  (s/keys :req [::http-method
                ::path]
          :opt [::request
                ::response
                ::parameters
                ::description]))

(s/def ::op-name string?)
(s/def ::op->info
  (s/map-of ::op-name ::op-info))

(s/def ::schema-name string?)
(s/def ::schemas (s/map-of ::schema-name ::json-schema))

(s/def ::descriptor
  (s/keys :req [::name
                ::title
                ::api-version
                ::revision
                ::endpoint
                ::parameters
                ::op->info
                ::schemas]))


(def api-descriptor-resource-path-base-directory
  "computesoftware/api-descriptors")

(defn api-descriptor-resource-path
  [api api-version]
  (format "%s/%s/%s/api-descriptor.edn"
          api-descriptor-resource-path-base-directory
          (name api)
          api-version))

(defn resolve-all-refs
  ([lookup-map resolve-ref] (resolve-all-refs lookup-map resolve-ref []))
  ([lookup-map resolve-ref path]
   (cond
     (map? lookup-map) (if-let [ref-val (get lookup-map "$ref")]
                         (if (some #{ref-val} path)
                           (assoc lookup-map ::recursive? true)
                           (resolve-all-refs (resolve-ref ref-val) resolve-ref (conj path ref-val)))
                         (into {}
                           (map (fn [[k v]]
                                  [k (resolve-all-refs v resolve-ref path)]))
                           lookup-map))
     (coll? lookup-map) (mapv (fn [x]
                                (resolve-all-refs x resolve-ref path))

                          lookup-map)
     :else lookup-map)))

(defn resolve-descriptor-refs
  [descriptor]
  (let [resolve-ref #(get-in descriptor [::schemas %])]
    (-> descriptor
        (update ::parameters resolve-all-refs resolve-ref)
        (update ::op->info resolve-all-refs resolve-ref)
        (update ::schemas resolve-all-refs resolve-ref))))

(defn read-descriptor
  [api api-version]
  (if-let [descriptor-resource (io/resource (api-descriptor-resource-path api api-version))]
    (with-open [rdr (io/reader descriptor-resource)
                push-rdr (java.io.PushbackReader. rdr)]
      (edn/read push-rdr))
    (throw (ex-info (format "Cannot find API descriptor file for %s %s"
                            (pr-str api) (pr-str api-version))
                    {:api         api
                     :api-version api-version}))))

(comment
  (def descriptor (read-descriptor "compute" "v1"))
  (keys descriptor)
  (first (::schemas descriptor))
  (count (filter (fn [[k v]] (get v "$ref")) (::schemas descriptor))))


(defn load-descriptor
  [api api-version]
  (resolve-descriptor-refs (read-descriptor api api-version)))

(comment
  (def loaded-descriptor (load-descriptor "compute" "v1"))
  (keys (::op->info loaded-descriptor))
  (get-in loaded-descriptor [::op->info "compute.instances.insert"]))


(defn get-op-info
  [descriptor op]
  (get-in descriptor [::op->info op]))

