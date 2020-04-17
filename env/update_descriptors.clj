(ns update-descriptors
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [clojure.data.json :as json]
    [java-http-clj.core :as http]
    [compute.gcp.impl.response :as response]
    [compute.gcp.impl.discovery-doc :as discovery-doc]
    [compute.gcp.descriptor :as descriptor]
    [clojure.spec.alpha :as s]))

(defn get-json
  [url]
  (let [resp (http/get url {:timeout 5000} {:as :input-stream})]
    (if (= 200 (:status resp))
      (json/read (io/reader (:body resp)))
      (throw (ex-info (str "Failed getting JSON " (:status resp))
                      {:cognitect.anomalies/category (response/http-status->category (:status resp))
                       :resp                         resp})))))

(defn list-discovery-docs
  []
  (-> (get-json "https://www.googleapis.com/discovery/v1/apis")
      (get "items")))

(defn spit-with-dirs
  [path content]
  (.mkdirs (.getParentFile (io/file path)))
  (spit path content))

(defn download-all-discovery-docs
  [discovery-docs-list]
  (pmap
    (fn [{:strs [discoveryRestUrl id]}]
      (try
        {::discovery-doc (get-json discoveryRestUrl)
         ::id            id}
        (catch Exception ex
          (merge {:cognitect.anomalies/category :cognitect.anomalies/fault
                  :id                           id
                  :ex                           ex}
                 (ex-data ex)))))
    discovery-docs-list))

(defn convert-one-download
  [{::keys [discovery-doc id] :as foo}]
  (let [descriptor (discovery-doc/discovery-doc->descriptor discovery-doc)]
    (if (s/valid? ::descriptor/descriptor descriptor)
      {::descriptor descriptor}
      {:cognitect.anomalies/category :cognitect.anomalies/incorrect
       :cognitect.anomalies/message  (str "Invalid descriptor for " id)
       :id                           id
       :explain-data                 (s/explain-data ::descriptor/descriptor descriptor)})))

(defn update!
  [downloads]
  (let [{failed-download-anoms true
         downloads             false} (group-by (comp boolean :cognitect.anomalies/category)
                                                downloads)
        converted (map convert-one-download downloads)
        {convert-anoms true
         successes     false} (group-by (comp boolean :cognitect.anomalies/category)
                                        converted)
        anoms (concat failed-download-anoms convert-anoms)]

    ;; Print anomalies
    (doseq [[anom-category anoms] (group-by :cognitect.anomalies/category anoms)]
      (println (format "[Warning] %s Swagger files failed with anomaly %s: %s"
                       (count anoms)
                       (name anom-category)
                       (str/join ", " (map :id anoms)))))

    (doseq [{::keys [descriptor]} successes
            :let [{:keys [::descriptor/name ::descriptor/api-version]} descriptor
                  path (descriptor/api-descriptor-resource-path name api-version)]]
      (spit-with-dirs (str "gcp-api-descriptors/" path) descriptor))))

(comment
  (def discovery-docs-list (list-discovery-docs))
  (def downloads (download-all-discovery-docs discovery-docs-list))
  (update! downloads)
  )

(defn -main
  [& args]
  (let [descriptors-dir (first args)
        _ (assert descriptors-dir "Missing descriptors dir")
        _ (println (str "Updating descriptor files in " descriptors-dir "..."))
        discovery-docs-list (list-discovery-docs)
        downloads (download-all-discovery-docs discovery-docs-list)]
    (update! downloads)
    (println "Success!")
    (shutdown-agents)))