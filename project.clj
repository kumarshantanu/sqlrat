(defproject sqlrat "0.3.0-SNAPSHOT"
  :description "SQL template library for Clojure, ClojureScript and ClojureCLR"
  :url "https://github.com/kumarshantanu/sqlrat"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[com.keminglabs/cljx "0.3.1"]
            [lein-clr     "0.2.1"]
            [lein-cascade "0.1.2"]]
  :clr {:cmd-templates {:clj-url "http://sourceforge.net/projects/clojureclr/files/clojure-clr-1.4.1-Debug-4.0.zip/download"
                        :clj-zip "clojure-clr-1.4.1-Debug-4.0.zip"
                        :clj-dep [[?PATH "mono"] ["target/clr/clj/Debug 4.0" %1]]
                        ;; :clj-exe is defined in 1.4 and 1.5 profiles
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
  :profiles {:1847 {:dependencies [[org.clojure/clojurescript "0.0-1847"]]}
             :1853 {:dependencies [[org.clojure/clojurescript "0.0-1853"]]}
             :1859 {:dependencies [[org.clojure/clojurescript "0.0-1859"]]}
             :1877 {:dependencies [[org.clojure/clojurescript "0.0-1877"]]}
             :1885 {:dependencies [[org.clojure/clojurescript "0.0-1885"]]}
             :1889 {:dependencies [[org.clojure/clojurescript "0.0-1889"]]}
             :1909 {:dependencies [[org.clojure/clojurescript "0.0-1909"]]}
             :1913 {:dependencies [[org.clojure/clojurescript "0.0-1913"]]}
             :1933 {:dependencies [[org.clojure/clojurescript "0.0-1933"]]}
             :1934 {:dependencies [[org.clojure/clojurescript "0.0-1934"]]}
             :1978 {:dependencies [[org.clojure/clojurescript "0.0-1978"]]}
             :2014 {:dependencies [[org.clojure/clojurescript "0.0-2014"]]}
             :2024 {:dependencies [[org.clojure/clojurescript "0.0-2024"]]}
             :2030 {:dependencies [[org.clojure/clojurescript "0.0-2030"]]}
             :2060 {:dependencies [[org.clojure/clojurescript "0.0-2060"]]}
             :2067 {:dependencies [[org.clojure/clojurescript "0.0-2067"]]}
             :2075 {:dependencies [[org.clojure/clojurescript "0.0-2075"]]}
             :2080 {:dependencies [[org.clojure/clojurescript "0.0-2080"]]}
             :2120 {:dependencies [[org.clojure/clojurescript "0.0-2120"]]}
             :2127 {:dependencies [[org.clojure/clojurescript "0.0-2127"]]}
             :2134 {:dependencies [[org.clojure/clojurescript "0.0-2134"]]}
             :cb1 {:plugins [[lein-cljsbuild "1.0.0-alpha1"]]}
             :cb2 {:plugins [[lein-cljsbuild "1.0.0"]]}
             :jst {:plugins [[com.cemerick/clojurescript.test "0.1.0"]]
                   :hooks [leiningen.cljsbuild]
                   :cljsbuild {:builds [{:source-paths ["target/generated/cljs" "target/generated/test-cljs"]
                                         :compiler {:output-to "target/cljs/testable.js"
                                                    :optimizations :whitespace
                                                    :pretty-print true}}]
                               :test-commands {"tests-phantom" ["phantomjs" :runner "target/cljs/testable.js"]
                                               "tests-slimer"  ["slimerjs" :runner "target/cljs/testable.js"]}}}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-alpha3"]]}
             :jvm {:test-paths ["test-clj"]}
             ;; Tagged literal tests fail on CLR, so commented out
             :clr-1.4 {:clr {:cmd-templates {:clj-exe [[?PATH "mono"] [CLJCLR14_40 %1]]}}
                       :test-paths [#_"test-clj"]}
             :clr-1.5 {:clr {:cmd-templates {:clj-exe [[?PATH "mono"] [CLJCLR15_40 %1]]}}
                       :test-paths ["test-clj"]}
             :pkg {:source-paths ["target/generated/cljs"]}}
  :cascade {:clean [["clean"]]
            :cljx  [["cljx" "once"]]
            :ccljx [:clean :cljx]
            :1847 [["with-profile" "1847,jst,cb1" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1853 [["with-profile" "1853,jst,cb1" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1859 [["with-profile" "1859,jst,cb1" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1877 [["with-profile" "1877,jst,cb1" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1885 [["with-profile" "1885,jst,cb1" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1889 [["with-profile" "1889,jst,cb1" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1909 [["with-profile" "1909,jst,cb1" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1913 [["with-profile" "1913,jst,cb1" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1933 [["with-profile" "1933,jst,cb1" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1934 [["with-profile" "1934,jst,cb1" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1978 [["with-profile" "1978,jst,cb1" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2014 [["with-profile" "2014,jst,cb2" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2024 [["with-profile" "2024,jst,cb2" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2030 [["with-profile" "2030,jst,cb2" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2060 [["with-profile" "2060,jst,cb2" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2067 [["with-profile" "2067,jst,cb2" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2075 [["with-profile" "2075,jst,cb2" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2080 [["with-profile" "2080,jst,cb2" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2120 [["with-profile" "2120,jst,cb2" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2127 [["with-profile" "2127,jst,cb2" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2134 [["with-profile" "2134,jst,cb2" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :cljs-all [:1847 :1853 :1859 :1877 :1885 :1889 :1909 :1913 :1933 :1934
                       :1978 :2014 #_:2024 #_:2030  ; tests fail in 2024, 2030 due to array-map
                       :2060 :2067 :2075 :2080 :2120 :2127 :2134]
            :1.4 [["with-profile" "1.4,jvm" "test"]]
            :1.5 [["with-profile" "1.5,jvm" "test"]]
            :1.6 [["with-profile" "1.6,jvm" "test"]]
            :clj-all [:1.4 :1.5 :1.6]
            :clr-1.4 [:ccljx ["with-profile" "clr-1.4" "clr" "test"]]
            :clr-1.5 [:ccljx ["with-profile" "clr-1.5" "clr" "test"]]
            :pkg [["with-profile" "pkg" %1]]
            "test1.4" [:ccljx :1.4]
            "testdev" [:ccljx :1.5 :2134]
            "testjvm" [:ccljx :clj-all]
            "testclr" [:clr-1.4 :clr-1.5]
            "testjs"  [:ccljx :cljs-all]
            "testall" [:ccljx :clj-all :cljs-all "testclr"]
            "pkg"     [:ccljx :pkg]})

