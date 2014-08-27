(defproject sqlrat "0.3.0-SNAPSHOT"
  :description "SQL template library for Clojure, ClojureScript and ClojureCLR"
  :url "https://github.com/kumarshantanu/sqlrat"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[com.keminglabs/cljx "0.4.0"]
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
  :profiles {:2197 {:dependencies [[org.clojure/clojurescript "0.0-2197"]]}
             :2199 {:dependencies [[org.clojure/clojurescript "0.0-2199"]]}
             :2202 {:dependencies [[org.clojure/clojurescript "0.0-2202"]]}
             :2227 {:dependencies [[org.clojure/clojurescript "0.0-2227"]]}
             :2234 {:dependencies [[org.clojure/clojurescript "0.0-2234"]]}
             :2261 {:dependencies [[org.clojure/clojurescript "0.0-2261"]]}
             :2268 {:dependencies [[org.clojure/clojurescript "0.0-2268"]]}
             :2277 {:dependencies [[org.clojure/clojurescript "0.0-2277"]]}
             :2280 {:dependencies [[org.clojure/clojurescript "0.0-2280"]]}
             :2311 {:dependencies [[org.clojure/clojurescript "0.0-2311"]]}
             :2322 {:dependencies [[org.clojure/clojurescript "0.0-2322"]]}
             :cb1 {:plugins [[lein-cljsbuild "1.0.0-alpha1"]]}
             :cb2 {:plugins [[lein-cljsbuild "1.0.2"]]}
             :cb3 {:plugins [[lein-cljsbuild "1.0.3"]]}
             :jst {:plugins [[com.cemerick/clojurescript.test "0.3.1"]]
                   :hooks [leiningen.cljsbuild]
                   :cljsbuild {:builds [{:source-paths ["target/generated/cljs" "target/generated/test-cljs"]
                                         :compiler {:output-to "target/cljs/testable.js"
                                                    :optimizations :advanced ; required to run tests on NodeJS
                                                    :pretty-print true}}]
                               :test-commands {"tests-phantom" ["phantomjs" :runner      "target/cljs/testable.js"]
                                               "tests-slimer"  ["slimerjs"  :runner      "target/cljs/testable.js"]
                                               "tests-nodejs"  ["nodejs"    :node-runner "target/cljs/testable.js"]}}}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
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
            :2197 [["with-profile" "2197,jst,cb3" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2199 [["with-profile" "2199,jst,cb3" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2202 [["with-profile" "2202,jst,cb3" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2227 [["with-profile" "2227,jst,cb3" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2234 [["with-profile" "2234,jst,cb3" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2261 [["with-profile" "2261,jst,cb3,1.6" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2268 [["with-profile" "2268,jst,cb3,1.6" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2277 [["with-profile" "2277,jst,cb3,1.6" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2280 [["with-profile" "2280,jst,cb3,1.6" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2311 [["with-profile" "2311,jst,cb3,1.6" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :2322 [["with-profile" "2322,jst,cb3,1.6" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :cljs-all [:2197 :2199 :2202 :2227 :2234 :2261 :2268 :2277 :2280 :2311 :2322]
            :1.4 [["with-profile" "1.4,jvm" "test"]]
            :1.5 [["with-profile" "1.5,jvm" "test"]]
            :1.6 [["with-profile" "1.6,jvm" "test"]]
            :clj-all [:1.4 :1.5 :1.6]
            :clr-1.4 [:ccljx ["with-profile" "clr-1.4" "clr" "test"]]
            :clr-1.5 [:ccljx ["with-profile" "clr-1.5" "clr" "test"]]
            :pkg [["with-profile" "pkg" %1]]
            "test1.4" [:ccljx :1.4]
            "test1.5" [:ccljx :1.5]
            "testdev" [:ccljx :1.6 :2322]
            "testjvm" [:ccljx :clj-all]
            "testclr" [:clr-1.4 :clr-1.5]
            "testjs"  [:ccljx :cljs-all]
            "testall" [:ccljx :clj-all :cljs-all "testclr"]
            "pkg"     [:ccljx :pkg]})

