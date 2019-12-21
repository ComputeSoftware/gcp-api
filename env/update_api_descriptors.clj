(ns update-api-descriptors
  (:require
    [clojure.java.io :as io]
    [clojure.data.json :as json]
    [clojure.pprint :as pp]
    [cognitect.anomalies :as anom]
    [aleph.http :as http]
    [manifold.deferred :as deferred]
    [compute.gcp.impl.util :as util]
    [compute.gcp.impl.descriptor :as descriptor]))

(def base-url
  "https://api.apis.guru/v2/specs/googleapis.com/monitoring/v3/swagger.json")

(defn swagger-url-for-gcp-api
  [gcp-api version]
  (format "https://api.apis.guru/v2/specs/googleapis.com/%s/%s/swagger.json"
          gcp-api
          version))

(defn get-json
  [url]
  (deferred/chain
    (http/get url)
    (fn [resp]
      (if (= 200 (:status resp))
        (json/read (io/reader (:body resp)) :key-fn util/json-key)
        {::anom/category ::anom/fault
         :resp           resp}))))

(defn fetch-gcp-apis
  []
  @(get-json "https://www.googleapis.com/discovery/v1/apis"))

(def api-infos-to-publish
  (map (fn [{:keys [name version] :as info}]
         (assoc info :swagger-url (swagger-url-for-gcp-api name version)))
       [{:name    "compute"
         :version "v1"}
        {:name    "monitoring"
         :version "v3"}]))

(defn spit-with-dirs
  [path content]
  (.mkdirs (.getParentFile (io/file path)))
  (spit path content))

(defn get-write-actions
  [descriptors-dir api-infos]
  (map (fn [{:keys [name version swagger-url] :as api-info}]
         (let [swagger @(get-json swagger-url)]
           (if (::anom/category swagger)
             (assoc swagger :api-info api-info)
             (let [base-api-dir (io/file descriptors-dir name)]
               {:descriptor
                {:path    (io/file base-api-dir (descriptor/api-descriptor-resource-path name))
                 :content (descriptor/swagger->descriptor swagger)}
                :deps-edn
                {:path    (io/file base-api-dir "deps.edn")
                 :content {:paths ["."]}}}))))
       api-infos))

(defn write!
  [write-actions]
  (doseq [{:keys [descriptor deps-edn] :as action} write-actions]
    (if (::anom/category action)
      (do
        (println "[Error] Anomaly while getting swagger json.")
        (pp/pprint action))
      (do
        (spit-with-dirs (:path descriptor) (:content descriptor))
        (spit-with-dirs (:path deps-edn) (:content deps-edn))))))

(comment
  (write! (get-write-actions "../gcp-api-descriptors" api-infos-to-publish))
  )