(ns sqlrat.literal-test
  (:require
         [clojure.test :refer (is deftest with-test run-tests testing)]
         [sqlrat.macros :refer (deftemplate)])
  (:require [sqlrat.template :as core]))


(def r-one '#sqlrat/template ["SELECT * FROM" table "WHERE" id "=" :id])


(def r-two #sqlrat/template "SELECT * FROM emp WHERE emp_id = :id")


(deftest test-template-reader
  (testing "#sqlrat/template"
    (is (= (core/realize r-one {'table "emp" 'id "emp_id" :id 10} {})
           ["SELECT * FROM emp WHERE emp_id = ?" 10]))
    (is (= (core/realize r-two {:id 10} {})
           ["SELECT * FROM emp WHERE emp_id = ?" 10]))))
