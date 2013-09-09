(ns sqlrat.macros
  (:require [sqlrat.template :as t]
            [sqlrat.entity :as e]
            [sqlrat.relation :as rel]))


(defmacro deftemplate
  [sym tokens]
  `(def ~sym (t/make-or-parse-template ~tokens)))


(defmacro defentity
  [sym entity-meta col-kw col-meta & more]
  {:pre [(symbol? sym)
         (map? entity-meta)
         (keyword? col-kw)
         (map? col-meta)
         (even? (count more))
         (every? keyword? (map first (partition 2 more)))
         (every? map? (map second (partition 2 more)))]}
  (let [e-name (name sym)]
    `(def ~sym (e/make-entity ~e-name ~entity-meta
                              (array-map ~col-kw ~col-meta ~@more)))))


(defmacro defrelmap
  [sym & tokens] {:pre [(symbol? sym)
                        (even? (count tokens))]}
  `(def ~sym (rel/make-relmap (array-map ~@tokens))))


(comment
  (defentity Employee {:table "emp" :id [:emp-id]}
    :emp-id      {:insert? false}
    :emp-code    {}
    :first-name  {:colname "f_name"}
    :dept-code   {}
    :created-at  {:default #(java.sql.Timestamp. (System/currentTimeMillis))})
  (defentity Department {:table "dept" :id [:dept-id]}
    :dept-id   {:insert? false}
    :dept-code {}
    :dept-head {})
  (defrelmap rels
    [Employee :dept-code] [Department :dept-code])
  (insert-row Employee)
  (find-by-id Employee)
  (find-by-cols Employee :emp-code)
  (update-by-id Employee)
  (delete-by-id Employee))
