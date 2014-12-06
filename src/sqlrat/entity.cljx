(ns sqlrat.entity
  (:require [clojure.string :as str]
            [clojure.set    :as set]
            [sqlrat.template :as t]
            [sqlrat.util :as u]
            [sqlrat.entity.crud :as crud]
            [sqlrat.entity.identifier :as ii])
  (#+clj :require #+cljs :require-macros [sqlrat.util-macros :as um]))


;; ===== Entity identification =====


(defn entity?
  [x]
  (ii/entity? x))


(defn make-entity
  [entity-name entity-meta col-kw-meta-map] {:pre [(string? entity-name)
                                                   (map? entity-meta)
                                                   (map? col-kw-meta-map)]}
  (let [{:keys [table id]} entity-meta
        idcols             id
        all-colspecs (map #(apply ii/make-colspec %) col-kw-meta-map)
        colkeyw-spec-map (->> all-colspecs ; array-map maintains order of columns
                              (map vector (map :colkeyw all-colspecs))
                              flatten
                              (apply array-map))
        colname-spec-map (->> all-colspecs ; array-map maintains order of columns
                              (map vector (map :colname all-colspecs))
                              flatten
                              (apply array-map))]
    (when (and idcols (not (or (list? idcols) (vector? idcols))))
      (u/throw-str "ID columns: Expected a list or a vector, found "
                   (pr-str idcols)))
    (when (seq idcols)
      (let [spcols-set (set (keys col-kw-meta-map))
            idcols-set (set idcols)
            defaulters (set/difference idcols-set spcols-set)]
        (when (seq defaulters)
          (u/throw-str "ID columns " (sort defaulters)
                       " are not among specified columns " (keys col-kw-meta-map)))))
    (ii/create-entity entity-name table idcols colkeyw-spec-map colname-spec-map)))


(defn entity-instance-or-coll?
  [x]
  (and (coll? x)
       (entity? (ii/get-annotated-entity x))))


(defn make-entity-instance
  [e m] {:pre [(um/verify (entity? e) e)
               (um/verify (map? m) m)
               (um/verify (ii/assert-valid-cols e (keys m) "Entity") m)]}
  (ii/annotate-instance-entity e m))


(defn make-entity-instance-coll
  [e c] {:pre [(um/verify (entity? e) e)
               (um/verify (u/only-coll? c) c)]}
  (ii/annotate-instance-entity e c))


(defn get-instance-or-coll-entity
  [x] {:pre [(um/verify (coll? x) x)]}
  (ii/get-annotated-entity x))


;; ===== ResultSet to Entity =====


(defn entity-keys
  "Given entity `e` and query resultset column keys `key-seq`, look up as column
  names and return a seq of equivalent entity keys."
  [e key-seq] {:pre [(entity? e)
                     (seq key-seq)]}
  (let [name-spec-map (:colname-spec-map e)]
    (->> key-seq
        (map u/as-string)
        (map #(if (contains? name-spec-map %)
                (:colkeyw (get name-spec-map %))
                (let [row-keys (->> key-seq
                                    (map u/as-string)
                                    set)
                      ent-keys (->> name-spec-map
                                    keys
                                    set)]
                  (u/throw-str "Invalid column names: "
                               (pr-str (sort (set/difference row-keys ent-keys)))
                               " - valid unused names: "
                               (pr-str (sort (set/difference ent-keys row-keys)))))))
        ;; map is lazy, exception may not occur in time, so doall
        doall)))


;; ===== Entity to SQL =====


;; ----- Entity metadata in template -----


(def ^{:doc "Key used to indicate entity-placeholder map in a template `opts`."}
  ephmap-key ::eph-map)


(defn get-ephmap
  [template] {:pre [(um/verify (t/template? template) template)]}
  (-> (second template)
      (get ephmap-key)))


(defn has-ephmap?
  [template]
  (map? (get-ephmap template)))


(defn apply-ephmap
  ([template e colkeyws] {:pre [(um/verify (entity? e) e)
                                (um/verify (u/only-coll? colkeyws) colkeyws)
                                (um/verify (every? keyword? colkeyws) colkeyws)]}
     (apply-ephmap template {e colkeyws}))
  ([template ephmap] {:pre [(um/verify (t/template? template) template)
                            (um/verify (map? ephmap) ephmap)
                            (um/verify (every? entity? (keys ephmap)) ephmap)
                            (um/verify (every? u/only-coll? (vals ephmap)) ephmap)
                            (um/verify (every? #(every? keyword? %) (vals ephmap))
                                       ephmap)]}
     (->> [(get-ephmap template) ephmap]
          (apply merge-with (comp distinct concat))
          (array-map ephmap-key)
          (t/partial template {}))))


;; ----- Insert rows -----


(defn insert-row
  [e] {:pre [(ii/entity? e)]}
  (let [rcols (->> (vals (:colkeyw-spec-map e))
                   (filter :insert?)
                   (map (juxt :colkeyw :colname :default)))
        colks (map first rcols)
        dcols (->> rcols
                   (filter #(nth % 2))
                   (map (juxt first #(nth % 2)))
                   flatten
                   (apply array-map))
        ;; identifiers/symbols
        [iargs isyms] (crud/imap-colnames (map second rcols))]
    (-> (crud/sql-insert (crud/table 'table) (crud/colnames isyms)
                         (crud/colvals colks))
        (t/partial iargs)
        (t/partial dcols)
        (t/partial (if-let [n (:table e)] {'table n} {}))
        (apply-ephmap e colks))))


;; ----- Optional arguments -----


(defn distinct-rows
  "Applies to `find-xxx` (SELECT) calls only."
  [x]
  {:distinct-rows x})


(defn cols
  "Applies to `find-xxx` (SELECT) and `update-xxx` (UPDATE) calls only."
  [xs] {:pre [(um/verify (coll? xs) xs)
              (if (map? xs)
                (um/verify (every? keyword? (keys xs)) xs)
                (um/verify (every? keyword? xs) xs))]}
  {:cols xs})


(defn where-cols
  "Applies to `find-xxx` (SELECT), `update-xxx` (UPDATE) and `delete-xxx` (DELETE)
  calls only."
  [xs] {:pre [(um/verify ((some-fn seq? coll?) xs) xs)]}
  {:where-cols xs})


(defn where-op
  "Applies to `find-xxx` (SELECT), `update-xxx` (UPDATE) and `delete-xxx` (DELETE)
  calls only."
  [x] {:pre [(crud/and-or x)]}
  {:where-op x})


(defn order-cols
  "Applicable for `find-xxx` (SELECT) calls only."
  [xs] {:pre [(um/verify ((some-fn seq? coll?) xs) xs)]}
  {:order-cols xs})


;; ----- Find rows -----


(defn find-all
  [e & opts] {:pre [(um/verify (entity? e) e)
                    (um/verify (every? map? opts) opts)]}
  (let [{:keys [distinct-rows cols where-cols where-op order-cols]
         :or {distinct-rows false}} (apply merge opts)
        where-vcols (when where-cols (ii/as-where-cols e where-cols))
        where-keyws (map last where-vcols)]
    (-> (crud/sql-select
         (crud/distinct-rows distinct-rows)
         (crud/from-alias 'table)
         (crud/colnames (ii/get-colnames e (first (ii/resolve-cols e cols "SELECT"))))
         (crud/join-clauses [['table 'table]])
         (and where-cols (crud/where-cols where-vcols))
         (and where-op (crud/where-op where-op))
         (and order-cols (crud/order-cols (ii/as-order-cols e order-cols))))
        (t/partial (if-let [n (:table e)] {'table n} {}))
        (apply-ephmap e where-keyws))))


(defn find-where
  [e where-clause-cols & opts]
  (apply find-all e (where-cols where-clause-cols) opts))


(defn find-by-id
  [e & opts]
  (ii/assert-entity-has-id e)
  (apply find-where e (:idcols e) opts))


;; ----- Update rows -----


(defn update-all
  [e & opts] {:pre [(um/verify (entity? e) e)
                    (um/verify (every? map? opts) opts)]}
  (let [{:keys [cols where-cols where-op]} (apply merge opts)
        where-kset  (set (if (map? where-cols) (keys where-cols) where-cols))
        [update-cols
         ph-cols]   (let [[update-cols ph-cols] (ii/resolve-cols e cols "UPDATE")]
                      (if (empty? cols) (->> (map vector update-cols ph-cols)
                                             (remove (comp where-kset second))
                                             (reduce (fn [[ru rp] [u p]]
                                                       [(conj ru u) (conj rp p)])
                                                     [[] []]))
                          [update-cols ph-cols]))]
    (um/when-assert
     (um/verify ((some-fn nil? coll?) where-cols) where-cols)
     (if (map? where-cols)
       (um/verify (every? keyword? (keys where-cols)) where-cols)
       (um/verify (every? keyword? where-cols) where-cols))
     (um/verify (every? keyword? update-cols) update-cols)
     (let [kset-common (set/intersection (set ph-cols) (set where-kset))]
       (when (seq kset-common)
         (let [[c u w] (map (comp pr-str sort) [kset-common ph-cols where-cols])]
           (u/throw-str "Found overlapping placeholders " c
                        " between UPDATE " u " and WHERE " w " columns")))))
    (let [where-vcols (when where-cols (ii/as-where-cols e where-cols))
          where-keyws (map last where-vcols)
          uc-defaults (->> (ii/get-specs e update-cols)
                           (map vector ph-cols)
                           (remove (comp nil? :default last))
                           (mapcat (fn [[pc spec]] [pc (:default spec)]))
                           (apply array-map))]
      (-> (crud/sql-update
           (crud/table 'table)
           (crud/colnames (ii/get-colnames e update-cols))
           (crud/colvals ph-cols)
           (and where-cols (crud/where-cols where-vcols))
           (and where-op (crud/where-op where-op)))
          (t/partial (if-let [n (:table e)] {'table n} {}))
          (t/partial uc-defaults)
          (apply-ephmap e (concat update-cols where-keyws))))))


(defn update-where
  [e where-clause-cols & opts]
  (apply update-all e (where-cols where-clause-cols) opts))


(defn update-by-id
  [e & opts]
  (ii/assert-entity-has-id e)
  (apply update-where e (:idcols e) opts))


;; ----- Delete rows -----


(defn delete-all
  [e & opts]
  (let [{:keys [where-cols where-op]} (apply merge opts)
        where-vcols (when where-cols (ii/as-where-cols e where-cols))
        where-keyws (map last where-vcols)]
    (-> (crud/sql-delete
         (crud/table 'table)
         (and where-cols (crud/where-cols where-vcols))
         (and where-op (crud/where-op where-op)))
        (t/partial (if-let [n (:table e)] {'table n} {}))
        (apply-ephmap e where-keyws))))


(defn delete-where
  [e where-clause-cols & opts]
  (apply delete-all e (where-cols where-clause-cols) opts))


(defn delete-by-id
  [e & opts]
  (ii/assert-entity-has-id e)
  (apply delete-where e (:idcols e) opts))


;; ===== Entity relations =====


(defn make-relmap
  [equiv-colsets] {:pre [(um/verify (u/only-coll? equiv-colsets) equiv-colsets)
                         (um/verify (every? u/only-coll? equiv-colsets) equiv-colsets)
                         (um/verify (every? #(every? u/only-coll? %) equiv-colsets)
                                    equiv-colsets)]}
  (um/when-assert
   (doseq [each-equiv equiv-colsets]
     (when (< (count each-equiv) 2)
       (u/throw-str "Expected 2 or more column sets but found only "
                    (count each-equiv) ": " (pr-str each-equiv)))
     (doseq [each-colset each-equiv]
       (let [[e & cols] each-colset]
         (um/verify (ii/entity? e) e)
         (um/verify (every? keyword? cols) cols)
         (ii/assert-valid-cols e cols "Entity")
         (when-not (distinct? cols)
           (u/throw-str "Found duplicate columns: " (pr-str cols)))))
     (let [equiv-entities (map first each-equiv)]
       (when-not (distinct? equiv-entities)
         (u/throw-str "Found duplicate entities in same equivalent set: "
                      (pr-str equiv-entities)))))
   (let [all-colsets (apply concat equiv-colsets)]
     (when-not (distinct? all-colsets)
       (u/throw-str "Found duplicate column sets: "
                    (->> (frequencies all-colsets)
                         (filter #(> %2 1))
                         (mapcat key)
                         pr-str)))))
  (->> equiv-colsets
       (reduce (fn [m equiv-coll]
                 (let [equiv-set (set equiv-coll)]
                   (->> equiv-coll
                        (reduce (fn [m e-cols]
                                  (let [[e & cols] e-cols
                                        rest-ecols (disj equiv-set e-cols)]
                                    (->> rest-ecols
                                         (reduce (fn [m [rel-e & rel-cols]]
                                                   {e {rel-e [cols rel-cols]}})
                                                 {})
                                         (merge-with merge m))))
                                {})
                        (merge-with merge m))))
               {})))


(comment
  (make-relmap [[E1 :fk1] [E2 :pk2] [E3 :fk3]] ; ordinary keys
               ;; composite keys
               [[E3 :pk3 :uk3] [E4 :fk4 :nk4]])
  ;; returns the following (bidirectional inter-entity column mapping)
  {E1 {E2 [[:fk1] [:pk2]]
       E3 [[:fk1] [:fk3]]}
   E2 {E1 [[:pk2] [:fk1]]
       E3 [[:pk2] [:fk3]]}
   E3 {E1 [[:fk3] [:fk1]]
       E2 [[:fk3] [:pk2]]
       E4 [[:pk3 :uk3] [:fk4 :nk4]]}
   E4 {E3 [[:fk4 :nk4] [:pk3 :uk3]]}})


(defn assert-relmap
  [relmap] {:pre [(um/verify (map? relmap) relmap)]}
  (um/verify (every? entity? (keys relmap)) relmap)
  (um/verify (every? map? (vals relmap)) relmap)
  (let [adis (fn [xs] (when-not (apply distinct? xs)
                       (u/throw-str "Found duplicates: " (pr-str xs))))]
    (doseq [[base-entity rel-submap] relmap
            [rel-entity [base-cols rel-cols]] rel-submap]
      (um/verify (entity? rel-entity) rel-entity)
      (um/verify (u/only-coll? base-cols) base-cols)
      (um/verify (u/only-coll? rel-cols) rel-cols)
      (ii/assert-valid-cols base-entity base-cols "Entity")
      (ii/assert-valid-cols rel-entity rel-cols "Entity")
      (when (not= (count base-cols) (count rel-cols))
        (u/throw-str "Column count must be the same: "
                     (pr-str base-cols) " " (pr-str rel-cols)))
      (adis base-cols)
      (adis rel-cols)))
  relmap)


(defn relvals
  "Given relation mapping `relmap`, `template` and `eis` (entity instance or a
  collection of instances) return a map of placeholder keywords to values. Note
  that an entity-instance yields individual value, whereas an entiy-instance
  collection yields a collection of values."
  [relmap template & eis] {:pre [(assert-relmap relmap)
                                 (um/verify (t/template? template) template)]}
  (um/when-assert
   (doseq [each eis]
     (when-not (entity-instance-or-coll? each)
       (u/throw-str
        "Expected entity instance or collection of entity instances but found "
        (pr-str each))))
   (let [duplicate-entities (->> eis
                                 (map get-instance-or-coll-entity)
                                 frequencies
                                 (filter #(> (val %) 1))
                                 (map key))]
     (when (seq duplicate-entities)
       (u/throw-str "Found duplicate instances/collections of entities: "
                    (pr-str duplicate-entities)))))
  (let [ephmap (get-ephmap template)]
    (reduce
     (fn [m ei-or-coll]
       (let [[eic? ei] (if (map? ei-or-coll) [false ei-or-coll]
                           [true (first ei-or-coll)])
             ei-e (get-instance-or-coll-entity ei-or-coll)]
         (merge
          m (reduce
             (fn [m [te tcols]]
               (let [colset (set tcols)]
                 (merge ; entity-instance and template entity
                  m (if-let [[ei-cols te-cols] (get-in relmap [ei-e te])]
                      (->> (map vector ei-cols te-cols)
                           (filter (comp colset second))
                           (filter (comp ei first))
                           (mapcat (fn [[eick teck]] ; column keywords
                                     [teck (if-not eic? (get ei eick)
                                                   (->> ei-or-coll
                                                        (map #(get % eick))
                                                        vec))]))
                           (apply array-map))
                      (u/throw-str "Unrelated entities: "
                                   (pr-str ei-e) " and " (pr-str te))))))
             {} ephmap))))
     {} eis)))
