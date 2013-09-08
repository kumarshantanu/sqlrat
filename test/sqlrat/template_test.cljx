(ns sqlrat.template-test
  (#+clj :require #+cljs :require-macros
         [#+clj clojure.test #+cljs cemerick.cljs.test :refer (is deftest with-test run-tests testing)]
         [sqlrat.macros :refer (deftemplate)])
  (:require #+cljs [cemerick.cljs.test :as t]
            [sqlrat.template :as core]))


(defn pt
  [sql]
  (core/parse-template sql))


(deftest happy-parsing
  (testing "no keywords"
    (is (= (pt "SELECT * FROM emp") [{} {} "SELECT * FROM emp"]))
    (is (= (pt "SELECT a::integer, b FROM emp") [{} {} "SELECT a::integer, b FROM emp"]))
    (is (= (pt "SELECT (sal > 10000? 'A':'B')") [{} {} "SELECT (sal > 10000? 'A':'B')"])))
  (testing "with keywords"
    (is (= (pt "WHERE id = :id") [{} {} "WHERE id =" :id]))
    (is (= (pt "WHERE id = :id AND age > 50") [{} {} "WHERE id =" :id "AND age > 50"]))
    (is (= (pt "WHERE id = :id AND age > :age") [{} {} "WHERE id =" :id "AND age >" :age])))
  (testing "with literals"
    (is (= (pt "WHERE data = 'type:more'") [{} {} "WHERE data = 'type:more'"]))
    (is (= (pt "WHERE data = 'type :more'") [{} {} "WHERE data = 'type :more'"]))
    (is (= (pt "d = 'type :more' AND e = :ec") [{} {} "d = 'type :more' AND e =" :ec])))
  (testing "with delimiters"
    (is (= (pt "WHERE id=:id") [{} {} "WHERE id=" :id]))
    (is (= (pt "WHERE id IN (:v1, :v2)") [{} {} "WHERE id IN (" :v1 "," :v2 ")"]))
    (is (= (pt "WHERE a=:b+1") [{} {} "WHERE a=" :b "+1"]))))


(defn ct
  [& tokens]
  (let [t (core/make-template (butlast tokens))]
    (core/realize-syms t (last tokens))))


(deftest happy-contraction
  (testing "no keys"
    (is (= (ct "SELECT * FROM emp" {}) [{} {} "SELECT * FROM emp"]))
    (is (= (ct "SELECT * FROM" 'table {'table "emp"})
           [{'table "emp"} {} "SELECT * FROM emp"]))
    (is (= (ct "FROM" 'table "LIMIT 1" {'table "emp"})
           [{'table "emp"} {} "FROM emp LIMIT 1"]))
    (is (= (ct "FROM" 't1 "," 't2 {'t1 "emp" 't2 "dept"})
           [{'t1 "emp" 't2 "dept"} {} "FROM emp , dept"])))
  (testing "with keys"
    (is (= (ct "FROM" 't "WHERE sum=" :s {'t "emp" :s 10})
           [{'t "emp" :s 10} {} "FROM emp WHERE sum=" :s]))
    (is (= (ct "FROM emp WHERE gender=" :g "AND age=" :a {:a 30 :g "M"})
           [{:a 30 :g "M"} {} "FROM emp WHERE gender=" :g "AND age=" :a]))))


(defn rt
  [& tokens]
  (let [t (core/make-template (butlast tokens))]
    (core/realize t (last tokens) {})))


(defn st
  [args opts & tokens]
  (let [t (core/make-template tokens)]
    (core/realize t args opts)))


(defn sst
  [args opts & tokens]
  (let [t (core/make-template tokens)]
    (core/realize-syms t args opts)))


(deftest happy
  (testing "no-arg"
    (is (= (rt "SELECT 1" {}) ["SELECT 1"]) "only SQL, no arg"))
  (testing "simple-arg"
    (is (= (rt "SELECT * FROM" 'table {'table "emp"})
           ["SELECT * FROM emp"])
        "only one syarg")
    (is (= (rt "SELECT" 'col "FROM" 'table {'col "empname" 'table "emp"})
           ["SELECT empname FROM emp"])
        "two syargs")
    (is (= (rt "SELECT * FROM emp WHERE age >" :age {:age 30})
           ["SELECT * FROM emp WHERE age > ?" 30])
        "one kwarg")
    (is (= (rt "SELECT * FROM emp WHERE age >" :age "AND grade <" :grade {:age 30 :grade 5})
           ["SELECT * FROM emp WHERE age > ? AND grade < ?" 30 5])
        "two kwargs"))
  (testing "simple-syarg-kwarg"
    (is (= (rt "UPDATE" 'table "SET name =" :name {'table "emp" :name "John"})
           ["UPDATE emp SET name = ?" "John"])
        "one syarg, one kwarg")
    (is (= (rt "UPDATE" 'table "SET name =" :name "AND" 'col "=" :grade
               {'table "emp" 'col "grade"
                :name "John" :grade 5})
           ["UPDATE emp SET name = ? AND grade = ?" "John" 5])
        "two syargs, two kwargs"))
  (testing "Identifer and Placeholder transformation"
    (is (= (st {'table "emp" 'token "emp_code"} {:subst #(str \` % \` )}
               "SELECT" 'token "FROM" 'table)
           ["SELECT `emp_code` FROM `emp`"])
        "identifier decoration")
    (is (= (st {:emp-age 34 :gender "M"} {:place (core/pgsql-wrap-place)}
                 "SELECT * FROM emp WHERE emp_age >" :emp-age "AND gender =" :gender)
             ["SELECT * FROM emp WHERE emp_age > ?::integer AND gender = ?" 34 "M"])
        "placeholder decoration")
    (is (= (st {:emp-age 34 :gender "M"} {:place core/dollar-place}
                 "SELECT * FROM emp WHERE emp_age >" :emp-age "AND gender =" :gender)
             ["SELECT * FROM emp WHERE emp_age > $1 AND gender = $2" 34 "M"])
        "placeholder decoration with index")
    (is (= (st {:emp-age 34 :gender "M"} {:place core/at-param-place}
                 "SELECT * FROM emp WHERE emp_age >" :emp-age "AND gender =" :gender)
             ["SELECT * FROM emp WHERE emp_age > @param1 AND gender = @param2" 34 "M"])
         "placeholder decoration with index"))
  (testing "partial (only symbol substitution) realization"
    (is (= (sst {'table "emp" 'token "emp_code"} {}
               "SELECT" 'token "FROM" 'table)
           [{'table "emp" 'token "emp_code"} {}
            "SELECT emp_code FROM emp"]) "ordinary template with symbols only")
    (is (= (sst {'table "emp" 'token "emp_code"} {}
               "SELECT * FROM" 'table "WHERE" 'token "=" :emp-code)
           [{'table "emp" 'token "emp_code"} {}
            "SELECT * FROM emp WHERE emp_code =" :emp-code]) "no error on missing keywords")
    (is (= (sst {'table "emp" 'token "emp_code" :emp-code 10} {}
               "SELECT * FROM" 'table "WHERE" 'token "=" :emp-code)
           [{'table "emp" 'token "emp_code" :emp-code 10} {}
            "SELECT * FROM emp WHERE emp_code =" :emp-code]) "does not realize keywords")))


(deftest unhappy
  (testing "missing arg"
    (is (thrown-with-msg?
         #+clj Exception #+cljs js/Error
         #"Missing args: \(:foo\), surplus args: \(\)"
         (rt "SELECT * FROM bar WHERE foo =" :foo {}))
        "one missing kwarg")
    (is (thrown-with-msg?
         #+clj Exception #+cljs js/Error
         #"Missing args: \(table\), surplus args: \(\)"
         (rt "SELECT * FROM" 'table {}))
        "one missing syarg")
    (is (thrown-with-msg?
         #+clj Exception #+cljs js/Error
         #"Missing args: \(:foo table\), surplus args: \(\)"
         (rt "SELECT * FROM" 'table "WHERE foo =" :foo {}))
        "one missing syarg, one missing kwarg")
    (is (thrown-with-msg?
         #+clj Exception #+cljs js/Error
         #"Missing args: \(:foo table\), surplus args: \(:moo fable\)"
         (rt "SELECT * FROM" 'table "WHERE foo =" :foo {'fable "emp" :moo 20}))
        "one mis-spelt syarg, one mis-spelt kwarg")))


;; (def one #sqlrat/template ["SELECT * FROM" 'table "WHERE" 'id "=" :id])
(deftemplate one "SELECT * FROM" 'table "WHERE" 'id "=" :id)


(deftest test-deftemplate
  (testing "deftemplate"
    (is (= (core/realize one {'table "emp" 'id "emp_id" :id 10} {})
           ["SELECT * FROM emp WHERE emp_id = ?" 10]))))
