(ns sqlrat.template
  (:refer-clojure :exclude [partial])
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [sqlrat.util :as u]
            [sqlrat.template.parser :as ip]
            [sqlrat.template.internal :as it])
  (#+clj :require #+cljs :require-macros [sqlrat.util-macros :as um]))


(defn make-template
  "Given string/symbol/keyword tokens, create an SQL template and return it."
  [tokens] {:pre [(um/verify ((some-fn seq? coll?) tokens) tokens)
                  (um/verify (seq tokens) "Empty token collection")]}
  (um/when-assert
   (when-not ((some-fn string? symbol?) (first tokens))
     (u/throw-str "Expected first token to be string/symbol but found "
                  (pr-str (first tokens))))
   (when-not (every? (some-fn string? symbol? keyword?) (rest tokens))
     (u/throw-str "Expected tokens to be string/symbol/keyword but found "
                  (pr-str tokens))))
  (->> tokens
       (concat [{} {}])
       vec))


(defn parse-template
  "Given SQL string, parse it and return an SQL template."
  ([sql opts] {:pre [(string? sql)
                     (map? opts)]}
     (make-template (ip/parse-tokens sql opts)))
  ([sql]
     (make-template (ip/parse-tokens sql))))


(defn template?
  [t]
  (and (vector? t)
       (let [[args opts sql & tokens] t]
         (and (map? args)
              (map? opts)
              ((some-fn string? symbol?) sql)
              (every? (some-fn string? symbol? keyword?) tokens)))))


(defn make-or-parse-template
  "Parse template if argument is string, make template otherwise."
  [sql-or-tokens]
  (if (string? sql-or-tokens) (parse-template sql-or-tokens)
      (make-template sql-or-tokens)))


(defn realize-syms
  "Partially realize template for symbols only and return the template."
  ([t args opts] {:pre [(template? t)
                        (map? args)
                        (map? opts)]}
     (let [[p-args p-opts & tokens] t
           args (merge p-args args)
           {:keys [subst]
            :or {subst (fn [v] (str v))}
            :as m-opts} (merge p-opts opts)]
       ;; substitute the values
       (let [args (zipmap (keys args) (map #(if (fn? %) (%) %) (vals args)))]
         (loop [tokens tokens
                sqlvec []]
           (if (empty? tokens)
             (->> sqlvec
                  (concat [args m-opts])
                  vec)
             (let [each (first tokens)
                   svec (cond
                         ;; symbol
                         (and (symbol? each) (contains? args each))
                         (it/render-symbol sqlvec subst (get args each))
                         ;; string
                         (string? each) (it/sqlvec-conj sqlvec each)
                         :otherwise
                         (conj sqlvec each))]
               (recur (rest tokens) svec)))))))
  ([t args]
     (realize-syms t args {})))


(defn dollar-place
  "Arity-2 place fn for some Node.js libraries"
  [idx x]
  (str "$" idx))


(defn at-param-place
  "Arity-2 place fn for ADO.NET SqlCommand API"
  [idx x]
  (str "@param" idx))


(let [int-min -2147483648
      int-max 2147483647
      long-min -9223372036854775808
      long-max 9223372036854775807]
  (defn pgsql-wrap-place
    "Given arity-2 place fn `f`, return a PostgreSQL specific placeholder fn"
    ([f] {:pre [(um/verify (fn? f) f)]}
       (fn [idx x]
         (let [ph (f idx x)]
           (if (number? x)
             (if (zero? (rem x 1))
               (cond
                (and (>= x int-min) (<= x int-max)) (str ph "::integer")
                (and (>= x long-min) (<= x long-max)) (str ph "::bigint")
                :otherwise (str ph "::numeric"))
               (str ph "::numeric"))
             (str ph)))))
    ([]
       (pgsql-wrap-place (constantly "?")))))


(defn realize
  "Realize a template and return a vector containing SQL string and arguments.
  Supported `opts` keywords are :subst (arity-1 fn to substitute symbol value)
  and :place (arity-2 fn to return placeholder string, typically '?')"
  ([t args opts] {:pre [(template? t)
                        (map? args)
                        (map? opts)]}
     (let [[p-args p-opts & tokens] t
           args (merge p-args args)
           {:keys [subst place]
            :or {subst (fn [v] (str v))
                 place (fn [idx v] "?")}} (merge p-opts opts)
           expected-ss (set (filter symbol? tokens))
           expected-ks (set (filter keyword? tokens))]
       ;; error check
       (um/when-assert
        (when (or (some #(not (contains? args %)) expected-ss)
                  (some #(not (contains? args %)) expected-ks))
          (let [supplied-ss (set (filter symbol? (keys args)))
                supplied-ks (set (filter keyword? (keys args)))]
            (u/throw-str
             "Missing args: "
             (pr-str (concat (sort (set/difference expected-ks supplied-ks))
                             (sort (set/difference expected-ss supplied-ss))))
             ", surplus args: "
             (pr-str (concat (sort (set/difference supplied-ks expected-ks))
                             (sort (set/difference supplied-ss expected-ss))))))))
       ;; substitute the values
       (let [args (zipmap (keys args) (map #(if (fn? %) (%) %) (vals args)))]
         (loop [tokens tokens
                sqlvec []
                params []]
           (if (empty? tokens)
             (cons (str/join \space sqlvec) params)
             (let [each (first tokens)
                   v (get args each)
                   [xsqlvec xparams]
                   (cond (symbol? each)  [(it/render-symbol sqlvec subst v) params]
                         (keyword? each) (if (or (seq? v) (coll? v))
                                           (it/expand-keyword place v sqlvec params)
                                           (let [idx (inc (count params))]
                                             [(conj sqlvec (place idx v))
                                              (conj params v)]))
                         :otherwise      [(conj sqlvec each) params])]
               (recur (rest tokens) xsqlvec xparams)))))))
  ([t args]
     (realize t args {})))


(defn partial
  "Partially apply `args` and `opts` to template `t` and return the template."
  ([t args opts] {:pre [(template? t)
                        (map? args)
                        (map? opts)]}
     (-> t
         (update-in [0] merge args)
         (update-in [1] merge opts)))
  ([t args]
     (partial t args {})))
