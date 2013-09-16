(defproject sqlrat "0.3.0-SNAPSHOT"
  :description "SQL template library for Clojure, ClojureScript and ClojureCLR"
  :url "https://github.com/kumarshantanu/sqlrat"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[com.keminglabs/cljx "0.3.0"]
            [lein-clr "0.2.0"]]
  :clr {:cmd-templates {:clj-url "http://sourceforge.net/projects/clojureclr/files/clojure-clr-1.4.1-Debug-4.0.zip/download"
                        :clj-zip "clojure-clr-1.4.1-Debug-4.0.zip"
                        :clj-dep [#_"mono" ["target/clr/clj/Debug 4.0" %1]]
                        :clj-exe [#_"mono" [CLJCLR14_40 %1]]
                        :wget    ["wget" "--no-check-certificate" "--no-clobber" "-O" %1 %2]
                        :unzip   ["unzip" "-d" %1 %2]}
        :deps-cmds [;[:wget  :clj-zip :clj-url]
                    ;[:unzip "../clj" :clj-zip]
                    ]
        :main-cmd    [:clj-exe "Clojure.Main.exe"]
        :compile-cmd [:clj-exe "Clojure.Compile.exe"]}
  :cljx {:builds [{:source-paths ["src"]  :output-path "target/generated/clj"       :rules :clj}
                  {:source-paths ["test"] :output-path "target/generated/test-clj"  :rules :clj}
                  {:source-paths ["src"]  :output-path "target/generated/cljs"      :rules :cljs}
                  {:source-paths ["test"] :output-path "target/generated/test-cljs" :rules :cljs}]}
  :source-paths ["src"  "target/generated/clj" "resources"]
  :test-paths   ["test" "target/generated/test-clj"]
  :profiles {:jst {:dependencies [[org.clojure/clojurescript #_"0.0-1847" #_"0.0-1853" #_"0.0-1859" #_"0.0-1877" #_"0.0-1885" "0.0-1889"]
                                  [com.cemerick/clojurescript.test "0.0.4"]]
                   :plugins [[lein-cljsbuild "0.3.3"]]
                   :hooks [leiningen.cljsbuild]
                   :cljsbuild {:builds [{:source-paths ["target/generated/cljs" "target/generated/test-cljs"]
                                         :compiler {:output-to "target/cljs/testable.js"
                                                    :optimizations :whitespace
                                                    :pretty-print true}}]
                               :test-commands {"unit-tests" ["phantomjs" "runners/phantomjs.js" "target/cljs/testable.js"]}}}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :pkg {:source-paths ["target/generated/cljs"]}
             }
  :aliases {"1.4" ["with-profile" "1.4" "do" "clean," "cljx" "once,"]
            "dev" ["with-profile" "1.5,jst" "do" "clean," "cljx" "once,"]
            "clr" ["do" "clean," "cljx" "once," "clr"]
            "pkg" ["with-profile" "pkg" "do" "clean," "cljx" "once,"]
            })

