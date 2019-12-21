(ns compute.gcp.impl.util)

;; LispReader also has a pattern for this:
;; https://github.com/clojure/clojure/blob/653b8465845a78ef7543e0a250078eea2d56b659/src/jvm/clojure/lang/LispReader.java#L66
(def kw-pattern #"[A-Za-z]+[-*+_'?<>=A-Za-z0-9]*")

(defn json-key
  [param]
  (if (re-matches kw-pattern param)
    (keyword param)
    param))