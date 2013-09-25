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


(defn rt
  [& tokens]
  (let [t (core/make-template (butlast tokens))]
    (core/realize t (last tokens) {})))


(defn st
  [args opts & tokens]
  (let [t (core/make-template tokens)]
    (core/realize t args opts)))


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
  (testing "multi kwarg values"
    (is (= (rt "FROM emp WHERE dept_id IN (" :ids ")" {:ids [1 2 3]})
           ["FROM emp WHERE dept_id IN ( ?, ?, ? )" 1 2 3])))
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
  (testing "stub realization"
    (testing "single parameter"
      (let [t (core/make-template ["SELECT * FROM emp WHERE e_code =" :e-code])
            m (core/stub t {:e-code 10})
            u (core/fill m {:e-code 20})]
        (is (= [{} {} "SELECT * FROM emp WHERE e_code =" :e-code] t))
        (is (= ["SELECT * FROM emp WHERE e_code = ?" :e-code] m))
        (is (= ["SELECT * FROM emp WHERE e_code = ?" 20] u))))
    (testing "multiple parameters"
      (let [t (core/make-template ["WHERE e_code =" :e-code "AND dept_code =" :dept-code])
            m (core/stub t {:e-code 10 :dept-code 20})
            u (core/fill m {:e-code 30 :dept-code 40})]
        (is (= [{} {} "WHERE e_code =" :e-code "AND dept_code =" :dept-code] t))
        (is (= ["WHERE e_code = ? AND dept_code = ?" :e-code :dept-code] m))
        (is (= ["WHERE e_code = ? AND dept_code = ?" 30 40] u))))
    (testing "multi-value parameter"
      (let [t (core/make-template ["WHERE e_code IN (" :e-code ")"])
            m (core/stub t {:e-code [10 20 30]})
            u (core/fill m {:e-code [30 40 50]})]
        (is (= [{} {} "WHERE e_code IN (" :e-code ")"] t))
        (is (= ["WHERE e_code IN ( ?, ?, ? )" :e-code] m))
        (is (= ["WHERE e_code IN ( ?, ?, ? )" 30 40 50] u))))))


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
        "one mis-spelt syarg, one mis-spelt kwarg"))
  (testing "missing value in mock template"
    (let [t (core/make-template ["SELECT * FROM emp WHERE e_code =" :e-code])]
      (is (thrown-with-msg?
           #+clj Exception #+cljs js/Error
           #"Missing args: \(:e-code\), surplus args: \(\)"
           (core/stub t {}))))
    (let [t (core/make-template ["SELECT * FROM emp WHERE e_code =" :e-code])
          m (core/stub t {:e-code 10})]
      (is (thrown-with-msg?
           #+clj Exception #+cljs js/Error
           #"No value found for :e-code"
           (core/fill m {}))))))


(deftemplate one ["SELECT * FROM" 'table "WHERE" 'id "=" :id])

(deftemplate two "SELECT * FROM emp WHERE emp_id = :id")


(deftest test-deftemplate
  (testing "deftemplate"
    (is (= (core/realize one {'table "emp" 'id "emp_id" :id 10} {})
           ["SELECT * FROM emp WHERE emp_id = ?" 10]))
    (is (= (core/realize two {:id 10} {})
           ["SELECT * FROM emp WHERE emp_id = ?" 10]))))
