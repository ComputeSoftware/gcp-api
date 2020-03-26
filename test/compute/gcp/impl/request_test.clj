(ns compute.gcp.impl.request-test
  (:require
    [clojure.test :refer :all]
    [provisdom.test.core]
    [compute.gcp.impl.request :as request]))

(deftest path-template-var-names-unit-test
  (is (= (list) (request/path-template-var-names "")))
  (is (= (list) (request/path-template-var-names "/foo/bar")))
  (is (= (list "foo") (request/path-template-var-names "/{foo}/bar")))
  (is (= (list "foo" "bar") (request/path-template-var-names "/{foo}/{bar}/"))))

(deftest resolve-path-template-unit-test
  (is (= "" (request/resolve-path-template "" {})))
  (is (= "/null/bar" (request/resolve-path-template "/{foo}/bar" {})))
  (is (= "/abc/bar" (request/resolve-path-template "/{foo}/bar" {:foo "abc" :extra "input"}))))

(deftest with-query-parameter-unit-test
  (is (= {:query-params {:a "abc"}} (request/with-query-parameter {} :a "abc"))))

(deftest with-path-parameter-unit-test
  (is (= {:uri ""} (request/with-path-parameter {} :foo "bar")))
  (is (= {:uri "https://example.co/bar/"} (request/with-path-parameter {:uri "https://example.co/{foo}/"} :foo "bar"))))

(deftest with-header-parameter-unit-test
  (is (= {:headers {"foo" "bar"}} (request/with-header-parameter {} :foo "bar")))
  (is (= {:headers {"a" "a" "foo" "bar"}}
         (request/with-header-parameter {:headers {"a" "a"}} :foo "bar"))))

(deftest with-form-parameter-unit-test
  (is (= {:form-params {"a" "a"}} (request/with-form-parameter {} :a "a"))))

(deftest with-auth-unit-test
  (is (= {:headers {"Authorization" "Bearer the-token"}} (request/with-auth {} "the-token"))))

;; TODO:
;(deftest with-request-parameters-unit-test)