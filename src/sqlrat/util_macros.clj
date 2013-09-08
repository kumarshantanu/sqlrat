(ns sqlrat.util-macros
  (:require [sqlrat.util :as u]))


(defmacro when-assert
  [& body]
  (when *assert*
    `(do ~@body)))


(defmacro assert-coll-is-not-map
  [coll]
  (when *assert*
    `(when (map? ~coll) (u/throw-format-msg
                         "Expected a linear collection but found map %s"
                         (pr-str ~coll)))))


(defmacro verify
  [expected found]
  (let [expected-form (pr-str expected)
        found-name (pr-str found)]
    `(if ~expected
       ~found
       (let [found# ~found]
         (u/throw-format-msg "Expected %s but found `%s` = %s (%s)"
                             ~expected-form ~found-name (pr-str found#)
                             (str (type found#)))))))


(defmacro verify-true
  [expected found]
  `(do (verify ~expected ~found)
       true))
