# sqlrat

SQL template library for Clojure, ClojureScript and ClojureCLR

It helps generate well formed SQL using templates and entities. While one can write hand-crafted templates to generate complex SQL, entities are useful to easily generate mundane [CRUD](http://en.wikipedia.org/wiki/Create,_read,_update_and_delete) SQL via auto-generated templates. This library does not execute SQL; it only outputs appropriate SQL with placeholders for JDBC, ODBC, ADO.NET and Node.js libraries.

## Usage

### Leiningen/Maven dependency

Not on Clojars yet. Run `lein pkg install` to install locally and include `[sqlrat "0.3.0-SNAPSHOT"]` in project dependencies.

### Namespaces

```clojure
(ns example
  (:require [sqlrat.template :as t]
            [sqlrat.entity :as e])
  ;; Require macros for Clojure and ClojureCLR
  (:require [sqlrat.macros :refer (deftemplate defentity)])
  ;; Require macros for ClojureScript
  (:require-macros '[sqlrat.macros :refer (deftemplate defentity)])
  ;; other stuff
  ..)
```

### Template

Templates are fundamental building blocks in _Sqlrat_ supporting identifier and
value _placeholders_. A simple template without any placeholder is as follows:

```clojure
(deftemplate r ["SELECT * FROM emp WHERE dept_id = 10 AND active"])
;; same as above (string templates are automatically parsed)
(deftemplate r "SELECT * FROM emp WHERE dept_id = 10 AND active")
```

Simple templates would not be useful, so they are almost always created with
placeholders. See sub-sections below.

#### Keyword arguments as value placeholders

Keyword arguments are value placeholders, used to realize the template into SQL
string. All of the following generate the same template with keyword args
`:dept-id`:

```clojure
(def s (t/make-template ["SELECT * FROM emp WHERE dept_id =" :dept-id "AND act =" :act]))
(def s (t/parse-template "SELECT * FROM emp WHERE dept_id = :dept-id AND act = :act"))
(def s #sqlrat/template ["SELECT * FROM emp WHERE dept_id =" :dept-id "AND act =" :act])
(def s #sqlrat/template "SELECT * FROM emp WHERE dept_id = :dept-id AND act = :act")
(deftemplate s ["SELECT * FROM emp WHERE dept_id =" :dept-id "AND act =" :act])
(deftemplate s "SELECT * FROM emp WHERE dept_id = :dept-id AND act = :act")
```

Templates must be realized in order to generate the SQL and arguments:

```clojure
(t/realize s {:dept-id 10 :act true})
;;=> ("SELECT * FROM emp WHERE dept_id = ? AND act = ?" 10 true)
```

##### Multi-value keyword arguments

Keywords can also act as multi-value placeholders, for example the following:

```clojure
(def t #sqlrat/template "SELECT * FROM emp WHERE id IN (:ids)")
(t/realize t {:ids [1 2 3]})
;;=> ("SELECT * FROM emp WHERE id IN ( ?, ?, ? )" 1 2 3)
```

#### Symbol arguments are identifier placeholders

Templates also support symbol arguments as identifier placeholders:

```clojure
(def t (t/make-template ["SELECT * FROM emp WHERE" 'dept-col "=" :dept-val]))
(t/realize t {'dept-col "dept_id" :dept-val 20})
;;=> ("SELECT * FROM emp WHERE dept_id = ?" 20)
```

Multi-values symbol arguments are joined using a default identifier delimiter.

```clojure
(t/realize t {'dept-col ["emp" "dept_id"] :dept-val 20})
;;=> ("SELECT * FROM emp WHERE emp.dept_id = ?" 20)
```

##### Identifier decoration

Should you want to quote the database identifiers, specify the `:subst` arity-1
(identifier-name) fn in options:

```clojure
(t/realize t {'dept-col ["emp" "dept_id"] :dept-val 20} {:subst #(str "`" % "`")})
;;=> ("SELECT * FROM emp WHERE `emp`.`dept_id` = ?" 20)
```

#### Value placeholder decoration

When using PostgreSQL, you may have to specially decorate the `?` placeholders
for integers on the JVM:

```clojure
(t/realize t {'dept-col "dept_id" :dept-val 20} {:place (t/pgsql-wrap-place)})
;;=> ("SELECT * FROM emp WHERE dept_id = ?::integer" 20)
```

Unless you are on the JVM (i.e. if you are using ClojureCLR or Node.js), you
may not want the `?` placeholder in the rendered SQL - use a custom arity-2
(index and column-name) `:place` function:

```clojure
;; for some Node.js database libraries
(t/realize t {'dept-col "dept_id" :dept-val 20} {:place t/dollar-place})
;;=> ("SELECT * FROM emp WHERE dept_id = $1" 20)

;; for ADO.NET SqlCommand API
(t/realize t {'dept-col "dept_id" :dept-val 20} {:place t/at-param-place})
;;=> ("SELECT * FROM emp WHERE dept_id = @param1" 20)
```

### Entity

Writing templates for mundane CRUD jobs is tiring. An entity represents a
database entity metadata. You can define them as follows:

```clojure
(defn ts-now [] (java.sql.Timestamp. (System/currentTimeMillis)))

(defentity BlogPost {:table "post" :id [:post-id]}  ; :id is primary key col
  :post-id {:insert? false}  ; column won't be included in INSERT statement
  :content {:colname "data"} ; column name rendered as 'data' in SQL
  :active  {:default true}   ; default value is supplied in INSERT statement
  :created {:default ts-now} ; default value is obtained by executing fn
  :updated {:default ts-now})
```

#### CRUD operations

A CRUD operation on an entity creates a template that you can realize just like
a hand-crafted template.

##### INSERT

```clojure
(def ip (e/insert-row BlogPost))  ; returns a template
(t/realize ip {:content "Foo bar"})
;;=> ("INSERT INTO post ( data , active , created , updated ) VALUES ( ? , ? , ? , ? )"
;;..  "Foo bar" true #inst "2013-09-09T12:40:52.765000000-00:00"
;;..  #inst "2013-09-09T12:40:52.765000000-00:00")
```

##### SELECT

```clojure
(def sp (e/find-by-id BlogPost))
(t/realize sp {:post-id 10})
;;=> ("SELECT post_id , data , active , created , updated FROM post WHERE post_id = ?" 10)
```

See `sqlrat.entity/find-all` and `sqlrat.entity/find-where` for other options.

##### UPDATE

```clojure
(def up (e/update-by-id BlogPost (e/cols [:content])))
(t/realize up {:post-id 10 :content "Foo bar baz"})
;;=> ("UPDATE post SET data = ? WHERE post_id = ?" "Foo bar baz" 10)
```

**Compare-and-swap (optimistic locking)** operations with UPDATE can be done
with renamed columns:

```clojure
(def sp (e/update-where BlogPost [:post-id :created]
                        (e/cols {:content :content :created :new-created})))
(t/realize sp {:post-id 10 :content "Foo bar baz quux" :created 9})
;;=> ("UPDATE post SET data = ? , created = ? WHERE post_id = ? AND created = ?"
;;..  "Foo bar baz quux" #inst "2013-09-09T15:13:45.112000000-00:00" 10 9)
```

See `sqlrat.entity/update-all` and `sqlrat.entity/update-where` for other options.

##### DELETE

```clojure
(def dp (e/delete-by-id BlogPost))
(t/realize dp {:post-id 10})
;;=> ("DELETE FROM post WHERE post_id = ?" 10)
```

See `sqlrat.entity/delete-all` and `sqlrat.entity/delete-where` for other options.


### Entity relations

TODO


## Development

### Running tests

You should have the following installed to run tests:

* PhantomJS - http://phantomjs.org/
* SlimerJS - http://slimerjs.org/
* .NET 4.0 SDK or Mono - http://mono-project.com/

```bash
lein cascade test1.4  # test with Clojure 1.4
lein cascade testdev  # test with Clojure 1.5 and ClojureScript
lein cascade testclr  # test with ClojureCLR (env var CLJCLR14_40 and CLJCLR15_40 should be defined)
lein cascade testall  # run all the tests
```

### Packaging a JAR

```bash
lein cascade pkg jar      # includes cljx, clj and cljs files into the JAR
lein cascade pkg install  # includes cljx, clj and cljs files into the JAR
```

## License

Copyright © 2013 Shantanu Kumar

Distributed under the Eclipse Public License, the same as Clojure.
