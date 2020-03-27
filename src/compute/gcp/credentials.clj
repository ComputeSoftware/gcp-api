(ns compute.gcp.credentials
  (:require
    [clojure.java.io :as io]
    [cognitect.anomalies :as anom]
    [clojure.data.json :as json])
  (:import (com.google.auth.oauth2 GoogleCredentials AccessToken)
           (java.util List)))

(defprotocol CredentialsProvider
  (fetch [_]))

(defn google-credentials-as-provider
  [^GoogleCredentials google-creds-obj]
  (reify CredentialsProvider
    (fetch [_]
      (let [refresh-result (try
                             (.refreshIfExpired google-creds-obj)
                             (catch Exception ex
                               {::anom/category ::anom/fault
                                ::anom/message  (str "Failed to refresh GCP token. "
                                                     (.getMessage ex))
                                :ex             ex}))]
        (if (::anom/category refresh-result)
          refresh-result
          {::access-token (.getTokenValue ^AccessToken (.getAccessToken google-creds-obj))})))))

(defn get-default
  []
  (google-credentials-as-provider
    ;; https://github.com/googleapis/google-auth-library-java#google-auth-library-oauth2-http
    (GoogleCredentials/getApplicationDefault)))

(defn from-service-account
  [{:keys [file creds scopes]}]
  (let [is (io/input-stream
             (cond
               file
               (io/file file)
               creds
               (.getBytes (json/write-str creds))))]
    (-> (GoogleCredentials/fromStream is)
        (.createScoped ^List scopes)
        (google-credentials-as-provider))))