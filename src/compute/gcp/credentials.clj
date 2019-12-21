(ns compute.gcp.credentials
  (:require
    [clojure.java.io :as io]
    [cognitect.anomalies :as anom]
    [clojure.data.json :as json])
  (:import (com.google.auth.oauth2 GoogleCredentials AccessToken)
           (java.util List)))

(defprotocol CredentialsProvider
  (fetch [_] ""))

;; https://github.com/googleapis/google-auth-library-java#google-auth-library-oauth2-http
(defn default-creds
  []
  (GoogleCredentials/getApplicationDefault))

(defn service-account-creds
  [{:keys [file creds scopes]}]
  (let [creds (-> (GoogleCredentials/fromStream
                    (io/input-stream
                      (cond
                        file
                        (io/file file)
                        creds
                        (.getBytes (json/write-str creds)))))
                  (.createScoped ^List scopes))]
    (reify CredentialsProvider
      (fetch [_]
        (let [refresh-result (try
                               (.refreshIfExpired creds)
                               (catch Exception ex
                                 {::anom/category ::anom/fault
                                  ::anom/message  (str "Failed to refresh GCP token. "
                                                       (.getMessage ex))
                                  :ex             ex}))]
          (if (::anom/category refresh-result)
            refresh-result
            {::access-token (.getTokenValue ^AccessToken (.getAccessToken creds))}))))))