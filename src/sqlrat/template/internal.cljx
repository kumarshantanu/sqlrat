(ns sqlrat.template.internal
  (:require [clojure.string :as str])
  (#+clj :require #+cljs :require-macros [sqlrat.util-macros :as um]))


(defn comma-separated
  [token & more]
  (str/join ", " (concat [token] more)))


(defn sqlvec-conj
  "Conj a realized+substituted symbol value `s` to `sqlvec` and return sqlvec."
  ([sqlvec s delim] {:pre [(vector? sqlvec)
                           (string? s)
                           (string? delim)]}
     (let [last-token (last sqlvec)]
       (if (string? last-token)
         (conj (pop sqlvec) (str last-token delim s))
         (conj sqlvec s))))
  ([sqlvec s]
     (sqlvec-conj sqlvec s " ")))


(defn render-symbol
  "Return SQL tokens vector"
  [sqlvec subst v]
  (sqlvec-conj sqlvec (if (or (seq? v) (coll? v))
                        (str/join "." (map subst v))
                        (str (subst v)))))


(defn expand-keyword
  "Return vector containing SQL tokens vector and params"
  [place coll sqlvec params]
  (um/assert-coll-is-not-map coll)
  (let [idxs (iterate inc (inc (count params)))]
    [(conj sqlvec (comma-separated (map place idxs coll)))
     (vec (concat params coll))]))
