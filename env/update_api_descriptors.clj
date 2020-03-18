(ns update-api-descriptors
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [clojure.data.json :as json]
    [cognitect.anomalies :as anom]
    [java-http-clj.core :as http]
    [com.climate.claypoole :as pool]
    [compute.gcp.impl.util :as util]
    [compute.gcp.impl.response :as response]
    [compute.gcp.impl.descriptor :as descriptor]))

(defn openapi-url-for-gcp-api
  [gcp-api version]
  (format "https://api.apis.guru/v2/specs/googleapis.com/%s/%s/openapi.json"
          gcp-api
          version))

(defn get-json
  [url]
  (let [resp (http/get url {:timeout 5000} {:as :input-stream})]
    (if (= 200 (:status resp))
      (json/read (io/reader (:body resp)) :key-fn util/json-key)
      {::anom/category (response/http-status->category (:status resp))
       :resp           resp})))

(defn fetch-gcp-apis
  []
  (get-json "https://www.googleapis.com/discovery/v1/apis"))

(defn fetch-api-openapi
  [gcp-api version]
  (get-json (openapi-url-for-gcp-api gcp-api version)))

(comment
  (def compute-swag (fetch-api-openapi "compute" "v1"))
  )

(defn api-infos-to-publish
  [gcp-apis]
  (map (fn [{:keys [name version]}]
         {:name        name
          :version     version
          :openapi-url (openapi-url-for-gcp-api name version)})
       (:items gcp-apis)))

(defn spit-with-dirs
  [path content]
  (.mkdirs (.getParentFile (io/file path)))
  (spit path content))

(defn get-all-specs
  [api-infos]
  (pool/upmap
    (pool/ncpus)
    (fn [{:keys [name version openapi-url] :as api-info}]
      (let [swagger (get-json openapi-url)]
        (assoc api-info :openapi-spec swagger)))
    api-infos))

(comment
  (def all-specs (get-all-specs (api-infos-to-publish (fetch-gcp-apis))))
  ;; specs with more than one server endpoint
  (->> get-all-specs
       (map :openapi-spec all-specs)
       (filter (complement ::anom/category))
       (filter #(> (count (:servers %)) 1))
       (count))
  )

(defn get-write-actions
  [descriptors-dir api-infos]
  (pool/upmap
    (pool/ncpus)
    (fn [{:keys [name version openapi-url] :as api-info}]
      (let [openapi-spec (get-json openapi-url)]
        (if (::anom/category openapi-spec)
          (assoc openapi-spec :api-info api-info)
          (let [base-api-dir (io/file descriptors-dir name)]
            {:descriptor
             {:path    (io/file base-api-dir (descriptor/api-descriptor-resource-path name version))
              :content (descriptor/openapi->descriptor openapi-spec)}
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
  (def write-actions (get-write-actions "gcp-api-descriptors" api-infos))
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