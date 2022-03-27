(ns update-descriptors
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [compute.gcp.descriptor :as descriptor]
    [compute.gcp.impl.discovery-doc :as discovery-doc]
    [compute.gcp.impl.response :as response]
    [java-http-clj.core :as http])
  (:import (java.util.zip GZIPOutputStream)))

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
  [{:keys [path content output-stream-fn]}]
  (.mkdirs (.getParentFile (io/file path)))
  (with-open [os (if output-stream-fn
                   (output-stream-fn path)
                   (io/output-stream (io/file path)))]
    (io/copy content os)))

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
  ([downloads] (update! downloads "gcp-api-descriptors"))
  ([downloads base-dir]
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

     (let [successes (map (fn [{::keys [descriptor] :as m}]
                            (assoc m
                              ::lib-dir (io/file
                                          (::descriptor/name descriptor)
                                          (::descriptor/api-version descriptor))))
                       successes)
           all-deps-edn-content {:paths (mapv (comp (memfn getPath) ::lib-dir) successes)}]
       (spit-with-dirs
         {:path    (io/file base-dir "deps.edn")
          :content (pr-str all-deps-edn-content)})

       (doseq [{::keys [descriptor lib-dir]} successes
               :let [{::descriptor/keys [name api-version]} descriptor
                     deps-edn-path (io/file lib-dir "deps.edn")
                     descriptor-path (io/file lib-dir (descriptor/api-descriptor-resource-path name api-version))
                     descriptor-output-file (io/file base-dir descriptor-path)]]
         (spit-with-dirs {:path             descriptor-output-file
                          :content          (pr-str descriptor)
                          :output-stream-fn #(GZIPOutputStream. (io/output-stream %))})
         (spit-with-dirs {:path    (io/file base-dir deps-edn-path)
                          :content (pr-str {:paths ["."]})}))))))

(comment
  (def discovery-docs-list (list-discovery-docs))
  (def downloads (download-all-discovery-docs discovery-docs-list))
  (update! downloads)
  )

(comment
  (def discovery-docs (map ::discovery-doc downloads))

  (into []
    (comp
      (filter (fn [doc]
                (not-empty (get doc "servicePath"))))
      (map (fn [doc]
             (select-keys doc ["servicePath" "name"]))))
    discovery-docs)

  ;; all paths
  (for [doc discovery-docs
        method (discovery-doc/collect-all-resource-methods (get doc "resources"))]
    [(get doc "name") (get method "path")])

  ;; services without flatPath
  (for [doc discovery-docs
        method (discovery-doc/collect-all-resource-methods (get doc "resources"))
        :when (empty? (get method "flatPath"))]
    [(get doc "name") (get method "id")])

  ;; freqs of flatPath and path
  (frequencies
    (for [doc discovery-docs
          method (discovery-doc/collect-all-resource-methods (get doc "resources"))]
      (let [v-or-nil #(when-let [x (not-empty %)] x)
            flat-path (v-or-nil (get method "flatPath"))
            path (v-or-nil (get method "path"))]
        [(boolean flat-path)
         (boolean path)])))
  )

(defn -main
  [& args]
  (let [descriptors-dir (first args)
        _ (assert descriptors-dir "Missing descriptors dir")
        _ (println (str "Updating descriptor files in " descriptors-dir "..."))
        discovery-docs-list (list-discovery-docs)
        downloads (download-all-discovery-docs discovery-docs-list)]
    (update! downloads descriptors-dir)
    (println "Success!")
    (shutdown-agents)))
