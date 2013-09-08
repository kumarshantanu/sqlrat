(ns sqlrat.entity.crud
  (:require [clojure.string :as str]
            [sqlrat.template :as t]
            [sqlrat.util :as u]
            [sqlrat.entity.identifier :as ii])
  (#+clj :require #+cljs :require-macros [sqlrat.util-macros :as um]))


;; ===== Basic verifiers =====


(defn and-or
  [x]
  (let [sx (str/lower-case (u/as-string x))]
    (case sx
      "and" "AND"
      "or" "OR"
      (u/throw-format-msg "Expected operator \"AND\" or \"OR\" but found %s"
                          (pr-str x)))))


;; ===== Assertion helpers =====


(defn db-iden?
  [x]
  (or (symbol? x)
      (string? x)))


(defn assert-iden
  [x xname] {:pre [(um/verify (string? xname) xname)]}
  (when-not (db-iden? x)
    (u/throw-format-msg "Expected %s to be symbol or string, but found %s"
                        xname (pr-str x))))


(defn assert-every-iden
  [xs xname] {:pre [(um/verify (seq xs) xs) (um/verify (string? xname) xname)]}
  (when-not (every? db-iden? xs)
    (u/throw-format-msg "Expected every %s to be symbol or string, but found %s"
                        xname (pr-str (remove db-iden? xs)))))


(defn not-symbol?
  [x]
  (not (symbol? x)))


(defn assert-every-val
  [xs xname] {:pre [(um/verify (seq xs) xs) (um/verify (string? xname) xname)]}
  (when-not (every? not-symbol? xs)
    (u/throw-format-msg
     "Expected every %s to be a value or placeholder keyword, but found %s"
     xname (pr-str (remove not-symbol? xs)))))


(defn assert-and-or
  [x]
  (if-let [y (and-or x)]
    y
    (u/throw-format-msg "Expected AND or OR but found %s" (pr-str x))))


(defn assert-table-colnames-colvals
  [table colnames colvals]
  (um/when-assert
   (assert-iden table "table name")
   (assert-every-iden colnames "column name")
   (assert-every-val colvals "column value")
   (when (not= (count colnames) (count colvals))
     (u/throw-format-msg
      "Expected same number of colnames (%d) and colvals (%d)"
      (count colnames) (count colvals)))))


;; ===== Clause helpers =====


(defn ireduce
  "Given fn [idx x] `f` (returns [argmap y]) and collection `xs`, map `f` over
  `xs` reducing the results into [argmap ys] and return it."
  [f xs] {:pre [(um/verify (fn? f) f) (um/verify (seq xs) xs)]}
  (->> xs
       (map-indexed f)
       (reduce (fn [[argmap ys] [m y]]
                 [(merge argmap m) (conj ys y)])
               [{} []])))


(defn iden-sym
  [colname] {:pre [(string? colname)]}
  (gensym (str colname "-")))


(defn imap-colnames
  "Return [argmap colsyms] such that each of colsyms is a symbol. `f` transforms
  (e.g. qualify with an alias) the column name."
  ([argmap f colnames] {:pre [(um/verify (map? argmap) argmap)
                              (um/verify (fn? f) f)
                              (um/verify (every? db-iden? colnames) colnames)]}
     (ireduce (fn [idx x] (if (symbol? x)
                           (if-let [cn (get argmap x)]
                             [{x (f cn)} x]
                             (u/throw-format-msg
                              "No column-name for column-symbol %s in %s"
                              (pr-str x) (pr-str argmap)))
                           (let [sym (iden-sym x)] [{sym (f x)} sym])))
              colnames))
  ([colnames] {:pre [(um/verify (every? db-iden? colnames) colnames)]}
     (ireduce (fn [idx x] (if (symbol? x) [{} x]
                             (let [sym (iden-sym x)] [{sym x} sym])))
              colnames)))


(defn imap-colvals
  "Return [argmap colkeys] such that each of colkeys is a keyword."
  [colnames colvals] {:pre [(um/verify (= (count colnames) (count colvals))
                                       [(count colnames) (count colvals)])]}
  (let [colnames (vec colnames)
        make-key (fn [i] (keyword (gensym (str (nth colnames i) "-"))))]
    (ireduce (fn [i v] (if (keyword? v) [{} v]
                          (let [k (make-key i)] [{k v} k])))
             colvals)))


(defn join-exp
  [table alias op & conds]
  (um/when-assert
   (assert-iden table "table name")
   (assert-iden alias "table alias")
   (when (> (count conds) 1)
     (assert-and-or op))
   (doseq [each conds]
     (assert-every-iden each "JOIN condition token")))
  (-> ["JOIN" table alias "ON"]
      (concat (interpose op conds))
      flatten))


(defn where-clause
  "Given collection of [colname operator colkeyw] triplets `where-cols` return a
  seq [where-argmap \"WHERE\" & where-tokens]"
  [where-cols where-op] {:pre [(um/verify (seq where-cols) where-cols)
                               (um/verify (every? #(= 3 (count %)) where-cols)
                                          where-cols)
                               (and-or where-op)]}
  (let [[argmap colsyms] (imap-colnames (map first where-cols))]
    (->> (map rest where-cols)
         (map cons colsyms)
         (interpose (and-or where-op))
         flatten
         (concat ["WHERE"])
         (cons argmap))))


(defn order-clause
  "Given collection of [colname operator] twins `order-cols` return a collection
  [argmap \"ORDER BY\" c1 t1 \",\" c2 t2 ..] - c1, c2 are column symbols and t1,
  t2 are \"ASC\" or \"DESC\" each."
  [order-cols] {:pre [(um/verify (seq order-cols) order-cols)
                      (um/verify (every? #(= 2 (count %)) order-cols)
                                 order-cols)]}
  (let [[argmap colsyms] (imap-colnames (map first order-cols))]
    (->> (map rest order-cols)
         (map cons colsyms)
         (interpose ",")
         flatten
         (concat ["ORDER BY"])
         (cons argmap))))


(defn iden-qualifier
  [qualifier] {:pre [(um/verify (db-iden? qualifier) qualifier)]}
  (fn [iden]
    (when (nil? iden)
      (u/throw-format-msg "Expected valid identifier but found nil"))
    (cons qualifier (u/as-vector iden))))


;; ===== CRUD template generators =====


(defn table
  [x]
  (assert-iden x "table name")
  {:table x})


(defn colnames
  [xs]
  (assert-every-iden xs "column name")
  {:colnames xs})


(defn colvals
  [xs]
  (assert-every-val xs "column value")
  {:colvals xs})


(defn insert
  "Given map arguments containing keywords :table, :colnames and :colvals,
  return an INSERT SQL template."
  [& opts] {:pre [(um/verify (every? (some-fn map? nil?) opts) opts)]}
  (let [{:keys [table colnames colvals]} (apply merge opts)]
    (assert-table-colnames-colvals table colnames colvals)
    (let [[csmap colsyms] (imap-colnames colnames)
          [kvmap colkeys] (imap-colvals colnames colvals)]
      (-> ["INSERT INTO" table "("]
          (concat (interpose "," colsyms))
          (concat [") VALUES ("])
          (concat (interpose "," colkeys))
          (concat [")"])
          t/make-template
          (t/partial csmap)
          (t/partial kvmap)))))


(defn distinct-rows
  [x]
  {:distinct-rows x})


(defn from-alias
  [x]
  {:from-alias x})


(defn join-clauses
  [x]
  {:join-clauses x})


(defn where-cols
  [x]
  {:where-cols x})


(defn where-op
  [x]
  {:where-op x})


(defn order-cols
  [x]
  {:order-cols x})


(defn select
  "Return a SELECT SQL template with specified clauses"
  [& opts] {:pre [(um/verify (every? (some-fn map? nil?) opts) opts)]}
  (let [{:keys [argmap distinct-rows colnames from-alias join-clauses
                where-cols where-op order-cols]
         :or {argmap {}
              where-op "AND"}} (apply merge opts)]
    (um/when-assert
     (assert-every-iden colnames "column name")
     (when (> (count join-clauses) 1)
       (assert-iden from-alias "table alias")))
    (let [[csmap colsyms] (imap-colnames argmap (if (> (count join-clauses) 1)
                                                  (iden-qualifier from-alias) identity)
                                         colnames)
          [[table alias] & joins] join-clauses
          [w-argmap & where-tokens] (when (seq where-cols) (where-clause where-cols where-op))
          [o-argmap & order-tokens] (when (seq order-cols) (order-clause order-cols))]
      (um/when-assert
       (assert-iden table "table name")
       (assert-iden alias "table alias")
       (let [aliases (map second join-clauses)]
         (when-not (some (partial = from-alias) aliases)
           (u/throw-format-msg "Alias %s is not among given aliases %s"
                               (pr-str from-alias) (pr-str aliases)))))
      (-> [(str "SELECT" (if distinct-rows " DISTINCT" ""))]
          (concat (interpose "," colsyms))
          (concat (if (> (count join-clauses) 1) ["FROM" table alias] ["FROM" table]))
          (concat (map join-exp joins))
          (concat where-tokens)
          (concat order-tokens)
          t/make-template
          (t/partial (merge argmap csmap w-argmap o-argmap))))))


(defn update
  [& opts] {:pre [(um/verify (every? (some-fn map? nil?) opts) opts)]}
  (let [{:keys [argmap table colnames colvals where-cols where-op]
         :or {where-op "AND"}} (apply merge opts)]
    (assert-table-colnames-colvals table colnames colvals)
    (let [[csmap colsyms] (imap-colnames colnames)
          [kvmap colkeys] (imap-colvals colnames colvals)
          setval-clauses  (->> colkeys
                              (map #(vector %1 "=" %2) colsyms)
                              (interpose ",")
                              flatten)
          [w-argmap & w-tokens] (when (seq where-cols) (where-clause where-cols where-op))]
      (-> ["UPDATE" table "SET"]
          (concat setval-clauses)
          (concat w-tokens)
          t/make-template
          (t/partial (merge argmap csmap kvmap w-argmap))))))


(defn delete
  [& opts] {:pre [(um/verify (every? (some-fn map? nil?) opts) opts)]}
  (let [{:keys [argmap table where-cols where-op]
         :or {where-op "AND"}} (apply merge opts)]
    (um/when-assert
     (assert-iden table "table name"))
    (let [[w-argmap & w-tokens] (when (seq where-cols) (where-clause where-cols where-op))]
      (-> ["DELETE FROM" table]
          (concat w-tokens)
          t/make-template
          (t/partial (merge argmap w-argmap))))))
