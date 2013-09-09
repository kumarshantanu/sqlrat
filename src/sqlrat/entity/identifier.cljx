(ns sqlrat.entity.identifier
  (:require [clojure.string :as str]
            [clojure.set    :as set]
            [sqlrat.util :as u])
  (#+clj :require #+cljs :require-macros [sqlrat.util-macros :as um]))


(defrecord Colspec [colkeyw colname insert? default])


(defrecord Entity [ename table idcols colkeyw-spec-map colname-spec-map])


(defn entity?
  [x]
  (instance? Entity x))


;; ====== Entity accessors ======


(defn get-specs
  "Given entity `e` and column keywords/names `cols` return corresponding Colspec
  records (nil if not found) in the order of `cols`."
  [e cols] {:pre [(um/verify (entity? e) e)
                  (um/verify ((some-fn coll? seq?) cols) cols)
                  (um/verify (every? (some-fn keyword? string?) cols) cols)]}
  (let [colkeyw-spec-map (:colkeyw-spec-map e)
        colname-spec-map (:colname-spec-map e)
        finder (some-fn colkeyw-spec-map colname-spec-map)]
    (map finder cols)))


(defn get-colnames
  "Given entity `e` and column keywords `colkeyws` return corresponding column
  names (nil if not found) in the order of the keywords."
  [e cols]
  (->> (get-specs e cols)
       (map :colname)))


(defn get-colkeyws
  "Given entity `e` and column names `colnames` return corresponding column
  keywords (nil if not found) in the order of the names."
  [e cols]
  (->> (get-specs e cols)
       (map :colkeyw)))


;; ====== Assertions helpers ======


(defn assert-entity
  [e]
  (if-not (entity? e)
    (u/throw-format-msg "Expected entity but found %s" (pr-str e))
    true))


(defn assert-entity-cols
  [e cols] {:pre [(um/verify (entity? e) e)
                  (um/verify ((some-fn coll? seq?) cols) cols)
                  (um/verify (every? keyword? cols) cols)]}
  (let [col-set (set cols)
        all-set (set (keys (:colkeyw-spec-map e)))
        missing (set/difference all-set col-set)
        surplus (set/difference col-set all-set)]
    (if (seq surplus)
      (u/throw-format-msg "Surplus cols: %s, missing cols: %s"
                          (pr-str (sort surplus)) (pr-str (sort missing)))
      true)))


(defn assert-valid-cols
  [e subject-cols cols-kind] {:pre [(um/verify (entity? e) e)
                                    (um/verify ((some-fn coll? seq?) subject-cols) subject-cols)
                                    (um/verify (every? keyword? subject-cols) subject-cols)
                                    (um/verify (string? cols-kind) cols-kind)]}
  (when (some #(not (contains? (:colkeyw-spec-map e) %)) subject-cols)
    (let [s-cols (set subject-cols)
          e-cols (set (keys (:colkeyw-spec-map e)))]
      (u/throw-format-msg
       "Invalid %s columns: %s - valid unused columns: %s"
       cols-kind (pr-str (sort (set/difference s-cols e-cols)))
       (pr-str (sort (set/difference e-cols s-cols))))))
  true)


(defn assert-entity-has-id
  [e] {:pre [(entity? e)]}
  (when-not (:idcols e)
    (u/throw-error-msg "No ID column is specified for the entity"))
  true)


;; ===== Entity generation =====


(defn kw-to-name
  [kw] {:pre [(or (keyword? kw) (symbol? kw))]}
  (str/replace (name kw) "-" "_"))


(defn any-to-name
  [any] {:pre [(not (nil? any))]}
  (cond (keyword? any) (kw-to-name any)
        (symbol? any)  (kw-to-name any)
        (string? any)  (str/replace any "-" "_")
        :otherwise     (any-to-name (str any))))


(defn make-colspec
  [colkeyw col-meta] {:pre [(keyword? colkeyw)
                            (map? col-meta)]}
  (let [{:keys [insert? colname default]
         :or {insert? true
              colname (kw-to-name colkeyw)}} col-meta]
    (->Colspec colkeyw colname insert? default)))


(defn create-entity
  [entity-name table idcols colkeyw-spec-map colname-spec-map]
  (->Entity entity-name table idcols colkeyw-spec-map colname-spec-map))


;; ===== Clause columns =====


(defn resolve-cols
  "Validate `candidate-cols` (map or collection) and return a collection
  [column-keywords placeholder-keywords]."
  [e candidate-cols cols-kind] {:pre [(um/verify (entity? e) e)
                                      (um/verify ((some-fn nil? coll?) candidate-cols)
                                                 candidate-cols)
                                      (um/verify (string? cols-kind) cols-kind)]}
  (um/when-assert
   (if (map? candidate-cols)
     (do (um/verify (every? keyword? (keys candidate-cols)) candidate-cols)
         (um/verify (every? keyword? (vals candidate-cols)) candidate-cols))
     (um/verify (every? keyword? candidate-cols) candidate-cols)))
  (if (empty? candidate-cols)
    (repeat 2 (keys (:colkeyw-spec-map e)))
    (if (map? candidate-cols)
      (let [cols (keys candidate-cols)]
        (assert-valid-cols e cols cols-kind)
        [cols (vals candidate-cols)])
      (do (assert-valid-cols e candidate-cols cols-kind)
          (repeat 2 candidate-cols)))))


(defn clause-cols
  "Given generator fn `f` (arity-0 for default, arity-1 for custom), entity `e`
  and clause `cols`, return ordered map of columns and tokens in a lockstep, e.g.
  {:c1 t1 :c2 t2 ...} - :c1, :c2 are column keywords, t1, t2 are string tokens."
  [f e cols clause-kind] {:pre [(fn? f)
                                (um/verify ((some-fn coll? seq?) cols) cols)
                                (um/verify (seq cols) cols)
                                (um/verify (string? clause-kind) clause-kind)]}
  (if (map? cols)
    (clause-cols f e (flatten (seq cols)) clause-kind)
    (do (assert-valid-cols e (filter keyword? cols) clause-kind)
        (loop [cols cols
               args []]
          (if (empty? cols)
            (apply array-map args)
            (let [[colkeyw custom & more] cols
                  [k v cols] (cond (keyword? custom) [colkeyw (f) (rest cols)]
                                   (next cols) [colkeyw (f custom) more]
                                   :otherwise  [colkeyw (f) (rest cols)])]
              (recur cols (conj args k v))))))))


(defn where=
  "Generator fn for WHERE clause. See `clause-cols`."
  ([x] {:pre [(string? x)]}
     x)
  ([]
     "="))


(defn asc-desc
  "Generator fn for ORDER-BY clause. See `clause-cols`."
  ([x] {:pre [(string? x)]}
     (let [lx (str/lower-case x)]
       (case lx
         "asc"  "ASC"
         "desc" "DESC"
         (u/throw-format-msg "Expected :asc or :desc but found %s" (pr-str x)))))
  ([]
     "ASC"))


(defn as-where-cols
  "Given a collection of entity column keywords `where-cols` (or a map of column
  keywords to operators) return a seq of [colname operator colkeyw] triplets."
  [e where-cols]
  (let [colmap (clause-cols where= e where-cols "WHERE")
        colkeyws (keys colmap)
        colopers (vals colmap)
        colnames (get-colnames e colkeyws)]
    (map vector colnames colopers colkeyws)))


(defn as-order-cols
  "Given a collection of entity column keywords `order-cols` (or a map of column
  keywords to operators) return a seq of [colname operator] twins."
  [e order-cols]
  (let [colmap (clause-cols asc-desc e order-cols "ORDER")
        colkeyws (keys colmap)
        colopers (vals colmap)
        colnames (get-colnames e colkeyws)]
    (map vector colnames colopers)))


;; ===== Entity instances ======


(def meta-entity-key :sqlrat-entity)


(defn annotate-instance-entity
  [e x] {:pre [(um/verify (entity? e) e)
               (um/verify (coll? x) x)]}
  (with-meta x {meta-entity-key e}))


(defn get-annotated-entity
  [ei] {:pre [(coll? ei)]}
  (get (meta ei) meta-entity-key))
