(ns sqlrat.util-macros
  (:require [sqlrat.util :as u]))


(defmacro when-assert
  [& body]
  (when *assert*
    `(do ~@body)))


(defmacro verify-return
  "Verify `expected` form -- throw exception if error, return `found` otherwise."
  [expected found]
  (let [expected-form (pr-str expected)
        found-name (pr-str found)]
    `(if ~expected
       ~found
       (let [found# ~found]
         (u/throw-format-msg "Expected %s but found `%s` = %s (%s)"
                             ~expected-form ~found-name (pr-str found#)
                             (str (type found#)))))))


(defmacro verify
  [expected found]
  `(do (verify-return ~expected ~found)
       true))
