(ns sqlrat.template.internal
  (:require [clojure.string :as str]
            [sqlrat.util :as u])
  (#+clj :require #+cljs :require-macros [sqlrat.util-macros :as um]))


(defn comma-separated
  [tokens] {:pre [(u/only-coll? tokens)]}
  (str/join ", " tokens))


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
  [place coll sqlvec params] {:pre [(um/verify (u/only-coll? coll) coll)
                                    (um/verify (vector? sqlvec) sqlvec)]}
  (let [idxs (iterate inc (inc (count params)))]
    [(->> (map place idxs coll) comma-separated (conj sqlvec))
     (vec (concat params coll))]))
