(ns sqlrat.relation
  (:require [clojure.set :as set]
            [sqlrat.entity :as e]
            [sqlrat.util   :as u]
            [sqlrat.entity.identifier :as ii])
  (#+clj :require #+cljs :require-macros [sqlrat.util-macros :as um]))


(comment
  (def ralias {E1 "e1"
               E2 "e2"
               E3 "e3"})
  (def relmap {E1 {E2 [[:k1] [:k2]]  ; :k1 and :k2 must be cols of E1 and E2 respectively
                   E3 [[:k1] [:k3]]}
               E2 {E1 [[:k2] [:k1]]
                   E3 [[:k2] [:k3]]
                   E5 [[:j2] [:j5]]}
               E3 {E1 [[:k3] [:k1]]
                   E2 [[:k3] [:k2]]}
               E5 {E2 [[:j5] [:j2]]}})
  (def relmap ;; number of equivalent columns must identical
    :returns
    {[E1 :fk1] #{[E2 :pk2] [E3 :fk3]}
     [E2 :pk2] #{[E1 :fk1] [E3 :fk3]}
     [E3 :fk3] #{[E1 :fk1] [E2 :pk2]}
     [E3 :pk3 :uk3] #{[E4 :fk4 :nk4]}
     [E4 :fk4 :nk4] #{[E3 :pk3 :uk3]}}
    ;; construct the relation map
    (make-relmap [[E1 :fk1] [E2 :pk2] [E3 :fk3]]
                 [[E3 :pk3 :uk3] [E4 :fk4 :nk4]]))
  ;; query the relation entity rows
  (find-rel rel-map [[E1 :fk1] [E2 :pk2] [E3 :fk3 :uk4]] [E3 :c1 :c2])
  ;; SELECT e3.* FROM E1 e1
  ;;             JOIN E2 e2
  ;;               ON e1.fk1 = e2.pk2
  ;;             JOIN E3 e3
  ;;               ON e2.pk2 = e3.fk3
  )


(defn qualify-cols
  "Given a string `qualifier`, column identifier map `iargs` (nil when `cols` has
  only keywords) and `cols` (each symbol/keyword) return qualified [argmap cols]."
  [qualifier iargs cols] {:pre [(um/verify (string? qualifier) qualifier)
                                (um/verify ((some-fn map? nil?) iargs) iargs)
                                (um/verify ((some-fn coll? seq? nil?) cols) cols)
                                (um/verify (every? (some-fn symbol? keyword?) cols)
                                           cols)]}
  (let [q (partial str qualifier ".")]
    (reduce (fn [[m svec] c]
              (let [g (fn [m n] (if (and (symbol? c) (contains? iargs c))
                                 (->> (get iargs c)
                                      u/as-vector
                                      (cons qualifier)
                                      (assoc m n))
                                 m))
                    n (if (keyword? c) (keyword (q (name c)))
                          (symbol (q c)))]
                [(-> m (dissoc c) (g n)) (conj svec n)]))
            [iargs []] cols)))


(defn qualify-inst
  "Given entity aliases `alias-map` and one or more entity instances, return
  a map using qualified column keys."
  [alias-map ei & more] {:pre [(um/verify (map? alias-map) alias-map)
                               (um/verify (every? ii/entity? (keys alias-map)) (keys alias-map))
                               (um/verify (every? string? (vals alias-map)) (vals alias-map))
                               (um/verify (e/entity-instance-or-coll? ei) ei)
                               (um/verify (every? e/entity-instance-or-coll? more) more)]}
  (->> (cons ei more)
       (map (fn [ei] (let [e (e/get-instance-or-coll-entity ei)]
                      (if-let [a (get alias-map e)]
                        (let [[_ cols] (qualify-cols a nil (keys ei))]
                          (u/array-zipmap cols (vals ei)))
                        ei))))
       (apply merge)))


(defn xref
  "Given entity `e`, entity alias `alias-map` and relation mapping `rel-colmap`
  return a modified `e` borrowing columns from relation entity/cols."
  [e alias-map rel-colmap]
  ;; TODO Needs thinking for CRUD operations
  )


(defn insert-rels
  [e alias-map rel-colmap]
  (fn [base-inst rel-inst]
    'TODO))


(defn find-rel
  "Given"
  ;; TODO tables, cols, distinct?, where/join cond, group-by(no!), order-by asc/desc
  [e alias-map rel-colmap [e & cols]] {:pre [(um/verify (ii/entity? e) e)
                                             (um/verify (map? alias-map) alias-map)
                                             ;; (assert-relmap rel-map)
                                       (ii/entity? e)
                                       (ii/assert-entity-cols e cols)]}
  )
