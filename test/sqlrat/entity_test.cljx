(ns sqlrat.entity-test
  (#+clj :require #+cljs :require-macros
         [#+clj clojure.test #+cljs cemerick.cljs.test :refer (is deftest with-test run-tests testing)]
         [sqlrat.macros :refer (defentity)])
  (:require #+cljs [cemerick.cljs.test :as t]
            [sqlrat.template :as core]
            [sqlrat.entity :as e]
            [sqlrat.util :as u]))


(defn rt
  [t args]
  (core/realize t args {}))


(deftest happy-single
  (let [em {:table "emp" :id [:emp-id]}
        cm (array-map
            :emp-id     {:insert? false}
            :emp-code   {:colname "e_code"}
            :first-name {:colname "f_name"}
            :dept-code  {}
            :created-at {:default #(do 10)})
        e  (e/make-entity "Employee" em cm)]
    (testing "Row to entity"
      (let [ks [:emp_id :e_code :f_name :dept_code :created_at]
            rs [(array-map
                 :emp_id 10 :e_code 20 :f_name "Joe" :dept_code 30 :created_at 40)
                (array-map
                 :emp_id 50 :e_code 60 :f_name "Sue" :dept_code 70 :created_at 80)]]
        (is (= [:emp-id :emp-code :first-name :dept-code :created-at]
               (e/entity-keys e ks)))
        (is (= [:emp-id :emp-code :first-name :dept-code :created-at]
               (e/entity-keys e (keys (first rs)))))))
    (testing "Insert - [C]rud"
      (let [ti (e/insert-row e)]
        (is (= (rt ti {:emp-code "c1" :first-name "John" :dept-code "d1"})
               ["INSERT INTO emp ( e_code , f_name , dept_code , created_at ) VALUES ( ? , ? , ? , ? )"
                "c1" "John" "d1" 10]))))
    (testing "c[R]ud (all cols) by cols"
      (let [tf (e/find-where e [:emp-code :dept-code])]
        (is (= (rt tf {:emp-code 10 :dept-code 20})
               ["SELECT emp_id , e_code , f_name , dept_code , created_at FROM emp WHERE e_code = ? AND dept_code = ?"
                10 20])))
      (let [tf (e/find-where e [:emp-code :dept-code] {:where-op :or})]
        (is (= (rt tf {:emp-code 10 :dept-code 20})
               ["SELECT emp_id , e_code , f_name , dept_code , created_at FROM emp WHERE e_code = ? OR dept_code = ?"
                10 20])))
      (let [tf (e/find-where e [:emp-code :dept-code] {:order-cols [:emp-code "desc" :dept-code]})]
        (is (= (rt tf {:emp-code 10 :dept-code 20})
               ["SELECT emp_id , e_code , f_name , dept_code , created_at FROM emp WHERE e_code = ? AND dept_code = ? ORDER BY e_code DESC , dept_code ASC"
                10 20]))))
    (testing "c[RUD] by cols"
      (let [tf (e/find-where e [:dept-code] (e/cols [:emp-code]))
            tu (e/update-where e [:dept-code] (e/cols [:emp-code]))
            ;; Compare-and-swap use case
            ts (e/update-where e [:dept-code] (e/cols {:emp-code :emp-code :dept-code :new-dept-code}))
            ;; Default-value use case
            tv (e/update-where e [:dept-code] (e/cols {:emp-code :emp-code :created-at :new-created-at}))
            tw (e/update-where e [:dept-code] (e/cols [:created-at]))
            ;; delete
            td (e/delete-where e [:emp-code :dept-code])]
        (is (= (rt tf {:dept-code 20})
               ["SELECT e_code FROM emp WHERE dept_code = ?" 20]))
        (is (= (rt tu {:emp-code 10 :dept-code 20})
               ["UPDATE emp SET e_code = ? WHERE dept_code = ?" 10 20]))
        (is (= (rt ts {:emp-code 10 :dept-code 20 :new-dept-code 30})
               ["UPDATE emp SET e_code = ? , dept_code = ? WHERE dept_code = ?" 10 30 20]))
        (is (= (rt tv {:emp-code 10 :dept-code 20}) ; should get default value :created-at
               ["UPDATE emp SET e_code = ? , created_at = ? WHERE dept_code = ?" 10 10 20]))
        (is (= (rt tw {:dept-code 20}) ; should pick up default value of :created-at
               ["UPDATE emp SET created_at = ? WHERE dept_code = ?" 10 20]))
        (is (= (rt td {:emp-code 10 :dept-code 20})
               ["DELETE FROM emp WHERE e_code = ? AND dept_code = ?" 10 20]))))
    (testing "c[RUD] by id"
      (let [tf (e/find-by-id e (e/cols [:emp-code :dept-code]))
            tu (e/update-by-id e (e/cols [:emp-code]))
            td (e/delete-by-id e)]
        (is (= (rt tf {:emp-id 10})
               ["SELECT e_code , dept_code FROM emp WHERE emp_id = ?" 10]))
        (is (= (rt tu {:emp-code "c1" :emp-id 10})
               ["UPDATE emp SET e_code = ? WHERE emp_id = ?" "c1" 10]))
        (is (= (rt td {:emp-id 10})
               ["DELETE FROM emp WHERE emp_id = ?" 10]))))))


(deftest happy-relation
  (let [blog-post (e/make-entity
                   "BlogPost" {:table "blog" :id [:blog-id]}
                   (array-map :blog-id {:insert? false}
                              :post    {}
                              :created-at {:default #(do 10)}))
        country (e/make-entity
                 "Country" {:table "country" :id [:country-id]}
                 (array-map :country-id {:insert? false}
                            :country-name {}))
        blog-comment (e/make-entity
                      "BlogComment" {:table "blog_comment" :id [:comment-id]}
                      (array-map :comment-id {:insert? false}
                                 :blog-id    {}
                                 :comment    {}
                                 :is-deleted {}
                                 :country-id {}
                                 :created-at {:default #(do 20)}))
        relmap (e/make-relmap [[[blog-post :blog-id] [blog-comment :blog-id]]
                               [[blog-comment :country-id] [country :country-id]]])]
    (testing "basic sanity"
      (is (= relmap (e/assert-relmap relmap)))
      (let [ephmap   {blog-post [:foo]}
            template (core/make-template ["hello"])]
        (is (nil? (e/get-ephmap template)))
        (is (= ephmap (->> ephmap
                           (e/apply-ephmap template)
                           e/get-ephmap)))))
    (testing "single value"
      (let [bp (e/make-entity-instance
                blog-post {:blog-id 1 :post "Sample" :created-at 10})
            ti (e/insert-row blog-comment)
            tf (e/find-where blog-comment [:blog-id])
            tu (e/update-where blog-comment [:blog-id :is-deleted])
            td (e/delete-where blog-comment [:blog-id :is-deleted])]
        ;; insert
        (is (e/has-ephmap? ti))
        (is (= {:blog-id 1} (e/relvals relmap ti bp)))
        ;; find
        (is (e/has-ephmap? tf))
        (is (= {:blog-id 1} (e/relvals relmap tf bp)))
        ;; update
        (is (e/has-ephmap? tu))
        (is (= {:blog-id 1} (e/relvals relmap tu bp)))
        ;; delete
        (is (e/has-ephmap? td))
        (is (= {:blog-id 1} (e/relvals relmap td bp)))))
    (testing "multi values"
      (let [ps (e/make-entity-instance-coll
                blog-post [{:blog-id 1 :post "Sample 1" :created-at 10}
                           {:blog-id 2 :post "Sample 2" :created-at 20}])
            cc (e/make-entity-instance
                country {:country-id 1 :country-name "Algeria"})
            tf (e/find-where   blog-comment {:blog-id "IN"})
            tu (e/update-where blog-comment {:blog-id "IN"})
            td (e/delete-where blog-comment {:blog-id "IN"})
            tm (e/find-where   blog-comment {:blog-id "IN" :country-id "="})]
        (is (= {:blog-id [1 2]} (e/relvals relmap tf ps)))
        (is (= {:blog-id [1 2]} (e/relvals relmap tu ps)))
        (is (= {:blog-id [1 2]} (e/relvals relmap td ps)))
        ;; mixed values
        (is (= {:blog-id [1 2] :country-id 1} (e/relvals relmap tm ps cc)))))))


(deftest unhappy
  (let [em {:table "emp" :id [:emp-id]}
        cm (array-map
            :emp-id     {:insert? false}
            :emp-code   {:colname "e_code"}
            :first-name {:colname "f_name"}
            :dept-code  {}
            :created-at {:default #(do 10)})
        e  (e/make-entity "Employee" em cm)]
    (testing "Row to entity"
      (let [ks [:emp_id :e_code :ef_name :dept_code :created_at]
            rs [(array-map
                 :emp_id 10 :e_code 20 :ef_name "Joe" :dept_code 30 :created_at 40)
                (array-map
                 :emp_id 50 :e_code 60 :ef_name "Sue" :dept_code 70 :created_at 80)]]
        (is (thrown-with-msg?
             #+clj Exception #+cljs js/Error
             #"Invalid column names: \(\"ef_name\"\) - valid unused names: \(\"f_name\"\)"
             (e/entity-keys e ks))
            "invalid column name in array-style")
        (is (thrown-with-msg?
             #+clj Exception #+cljs js/Error
             #"Invalid column names: \(\"ef_name\"\) - valid unused names: \(\"f_name\"\)"
             (e/entity-keys e (keys (first rs))))
            "invalid column name in row-seq style")))
    (testing "Insert (Crud)"
      (let [ti (e/insert-row e)]
        (is (thrown-with-msg?
             #+clj Exception #+cljs js/Error
             #"Missing args: \(:first-name\), surplus args: \(:f-name\)"
             (rt ti {:emp-code "c1" :f-name "John" :dept-code "d1"})))))
    (testing "cRud (all cols) by cols"
      (let [tf (e/find-where e [:emp-code :dept-code])]
        (is (thrown-with-msg?
             #+clj Exception #+cljs js/Error
             #"Missing args: \(:dept-code\), surplus args: \(:d-code\)"
             (rt tf {:emp-code 10 :d-code 20})))))
    (testing "cRUD by cols"
      (is (thrown-with-msg?
           #+clj Exception #+cljs js/Error
           #"Invalid SELECT columns: \(:e-code :e-id\) - valid unused columns: \(:created-at :dept-code :emp-code :emp-id :first-name\)"
           (e/find-where e [:dept-code] (e/cols [:e-code :e-id]))))
      (is (thrown-with-msg?
           #+clj Exception #+cljs js/Error
           #"Invalid UPDATE columns: \(:e-code\) - valid unused columns: \(:created-at :dept-code :emp-code :emp-id :first-name\)"
           (e/update-where e [:d-code] (e/cols [:e-code]))))
      (is (thrown-with-msg?
           #+clj Exception #+cljs js/Error
           #"Invalid WHERE columns: \(:d-code :e-code\) - valid unused columns: \(:created-at :dept-code :emp-code :emp-id :first-name\)"
           (e/delete-where e [:e-code :d-code])))
      (let [tf (e/find-where e [:dept-code] (e/cols [:emp-code]))
            tu (e/update-where e [:dept-code] (e/cols [:emp-code]))
            td (e/delete-where e [:emp-code :dept-code])]
        (is (thrown-with-msg?
             #+clj Exception #+cljs js/Error
             #"Missing args: \(:dept-code\), surplus args: \(:d-code\)"
             (rt tf {:d-code 20})))
        (is (thrown-with-msg?
             #+clj Exception #+cljs js/Error
             #"Missing args: \(:dept-code\), surplus args: \(:d-code\)"
             (rt tu {:emp-code 10 :d-code 20})))
        (is (thrown-with-msg?
             #+clj Exception #+cljs js/Error
             #"Missing args: \(:dept-code\), surplus args: \(:d-code\)"
             (rt td {:emp-code 10 :d-code 20})))))
    (testing "cRUD by id"
      (is (thrown-with-msg?
           #+clj Exception #+cljs js/Error
           #"Invalid SELECT columns: \(:d-code :e-code\) - valid unused columns: \(:created-at :dept-code :emp-code :emp-id :first-name\)"
           (e/find-by-id e (e/cols [:e-code :d-code]))))
      (is (thrown-with-msg?
           #+clj Exception #+cljs js/Error
           #"Invalid UPDATE columns: \(:e-code\) - valid unused columns: \(:created-at :dept-code :emp-code :emp-id :first-name\)"
           (e/update-by-id e (e/cols [:e-code]))))
      (let [tf (e/find-by-id e (e/cols [:emp-code :dept-code]))
            tu (e/update-by-id e (e/cols [:emp-code]))
            td (e/delete-by-id e)]
        (is (thrown-with-msg?
             #+clj Exception #+cljs js/Error
             #"Missing args: \(:emp-id\), surplus args: \(:e-id\)"
             (rt tf {:e-id 10})))
        (is (thrown-with-msg?
             #+clj Exception #+cljs js/Error
             #"Missing args: \(:emp-code :emp-id\), surplus args: \(:e-code :e-id\)"
             (rt tu {:e-code "c1" :e-id 10})))
        (is (thrown-with-msg?
             #+clj Exception #+cljs js/Error
             #"Missing args: \(:emp-id\), surplus args: \(:e-id :f-id\)"
             (rt td {:e-id 10 :f-id 20})))))))


(defentity Employee {:table "emp" :id [:emp-id]}
  :emp-id     {:insert? false}
  :emp-code   {:colname "e_code"}
  :first-name {:colname "f_name"}
  :dept-code  {}
  :created-at {:default #(do 10)})


(deftest test-defentity
  (is (e/entity? Employee)))
