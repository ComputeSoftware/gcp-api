(ns update-api-descriptors
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [clojure.data.json :as json]
    [cognitect.anomalies :as anom]
    [aleph.http :as http]
    [com.climate.claypoole :as pool]
    [manifold.deferred :as deferred]
    [compute.gcp.impl.util :as util]
    [compute.gcp.impl.http :as http-impl]
    [compute.gcp.impl.descriptor :as descriptor]))

(defn swagger-url-for-gcp-api
  [gcp-api version]
  (format "https://api.apis.guru/v2/specs/googleapis.com/%s/%s/swagger.json"
          gcp-api
          version))

(defn get-json
  [url]
  (deferred/chain
    (http/get url {:throw-exceptions false})
    (fn [resp]
      (if (= 200 (:status resp))
        (json/read (io/reader (:body resp)) :key-fn util/json-key)
        {::anom/category (http-impl/http-status->category (:status resp))
         :resp           resp}))))

(defn fetch-gcp-apis
  []
  @(get-json "https://www.googleapis.com/discovery/v1/apis"))

(defn api-infos-to-publish
  [gcp-apis]
  (map (fn [{:keys [name version]}]
         {:name        name
          :version     version
          :swagger-url (swagger-url-for-gcp-api name version)})
       (:items gcp-apis)))

(defn spit-with-dirs
  [path content]
  (.mkdirs (.getParentFile (io/file path)))
  (spit path content))

(defn get-write-actions
  [descriptors-dir api-infos]
  (pool/upmap
    (pool/ncpus)
    (fn [{:keys [name version swagger-url] :as api-info}]
      (let [swagger @(get-json swagger-url)]
        (if (::anom/category swagger)
          (assoc swagger :api-info api-info)
          (let [base-api-dir (io/file descriptors-dir name)]
            {:descriptor
             {:path    (io/file base-api-dir (descriptor/api-descriptor-resource-path name version))
              :content (descriptor/swagger->descriptor swagger)}
             :deps-edn
             {:path    (io/file base-api-dir "deps.edn")
              :content {:paths ["."]}}}))))
    api-infos))

(defn write!
  [write-actions]
  (let [{:keys [successful] :as actions} (group-by #(::anom/category % :successful) write-actions)
        anom-actions (dissoc actions :successful)]
    (doseq [anom-category (keys anom-actions)
            :let [anoms (get anom-actions anom-category)]]
      (println (format "[Warning] %s Swagger files were %s: %s"
                       (count anoms)
                       (name anom-category)
                       (str/join ", " (map (fn [{:keys [api-info]}]
                                             (str (:name api-info) ":" (:version api-info)))
                                           anoms)))))

    (doseq [{:keys [descriptor deps-edn]} successful]
      (do
        (spit-with-dirs (:path descriptor) (:content descriptor))
        (spit-with-dirs (:path deps-edn) (:content deps-edn))))))


(comment
  (def api-infos (api-infos-to-publish (fetch-gcp-apis)))
  (def write-actions (get-write-actions "../gcp-api-descriptors" api-infos))
  (write! write-actions)
  )

(defn -main
  [& args]
  (let [descriptors-dir (first args)
        _ (assert descriptors-dir "Missing descriptors dir")
        _ (println (str "Updating descriptor files in " descriptors-dir "..."))
        api-infos (api-infos-to-publish (fetch-gcp-apis))
        write-actions (get-write-actions descriptors-dir api-infos)]
    (write! write-actions)
    (println "Success!")
    (shutdown-agents)))