(ns sqlrat.util)


(defn throw-error-msg
  [msg] {:pre [(string? msg)]}
  (throw (new #+clj Exception #+cljs js/Error msg)))


(defn throw-format-msg
  [fmt & args] {:pre [(string? fmt)]}
  (-> (apply format fmt args)
      throw-error-msg))


(defn as-string
  [x]
  (if (or (symbol? x) (keyword? x))
    (name x)
    (str x)))


(defn as-vector
  [x] {:pre [(not (map? x))]}
  (cond (coll? x) (vec x)
        (nil? x)  []
        :else     [x]))


(defn echo
  [x]
  (println "[Echo]" (pr-str x))
  x)


(defn array-zipmap
  [ks vs]
  (->> (map vector ks vs)
       flatten
       (apply array-map)))


(defn only-coll?
  [x]
  (and ((some-fn coll? seq?) x)
       (not (map? x))))
