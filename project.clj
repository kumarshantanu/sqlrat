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
                        :clj-exe [[?PATH "mono"] [CLJCLR14_40 %1]]
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
             :jst {:plugins [[lein-cljsbuild "1.0.0-SNAPSHOT"]
                             [com.cemerick/clojurescript.test "0.1.0"]]
                   :hooks [leiningen.cljsbuild]
                   :cljsbuild {:builds [{:source-paths ["target/generated/cljs" "target/generated/test-cljs"]
                                         :compiler {:output-to "target/cljs/testable.js"
                                                    :optimizations :whitespace
                                                    :pretty-print true}}]
                               :test-commands {"tests-phantom" ["phantomjs" :runner "target/cljs/testable.js"]
                                               "tests-slimer"  ["slimerjs" :runner "target/cljs/testable.js"]}}}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-alpha1"]]}
             :jvm {:test-paths ["test-clj"]}
             :clr {:test-paths [#_"test-clj"]} ; tagged readers fail on CLR
             :pkg {:source-paths ["target/generated/cljs"]}}
  ;; :aliases {"1.4" ["with-profile" "1.4" "do" "clean," "cljx" "once,"]
  ;;           "dev" ["with-profile" "1.5,jst" "do" "clean," "cljx" "once,"]
  ;;           "clr" ["do" "clean," "cljx" "once," "clr"]
  ;;           "pkg" ["with-profile" "pkg" "do" "clean," "cljx" "once,"]}
  :cascade {:clean [["clean"]]
            :cljx  [["cljx" "once"]]
            :ccljx [:clean :cljx]
            :1847 [["with-profile" "1847,jst" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1853 [["with-profile" "1853,jst" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1859 [["with-profile" "1859,jst" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1877 [["with-profile" "1877,jst" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1885 [["with-profile" "1885,jst" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1889 [["with-profile" "1889,jst" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1909 [["with-profile" "1909,jst" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1913 [["with-profile" "1913,jst" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1933 [["with-profile" "1933,jst" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1934 [["with-profile" "1934,jst" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1978 [["with-profile" "1978,jst" "do" "cljsbuild" "clean," "cljsbuild" "test"]]
            :1.4 [["with-profile" "1.4,jvm" "test"]]
            :1.5 [["with-profile" "1.5,jvm" "test"]]
            :1.6 [["with-profile" "1.6,jvm" "test"]]
            :clr [["with-profile" "clr" "clr" "test"]]
            :pkg [["with-profile" "pkg" %1]]
            "test1.4" [:ccljx :1.4]
            "testdev" [:ccljx :1.5 :1978]
            "testjvm" [:ccljx :1.4 :1.5 :1.6]
            "testclr" [:ccljx :clr]
            "testall" [:ccljx :1.4 :1.5 :1.6 :1847 :1853 :1859 :1877 :1885 :1889 :1909 :1913 :1933 :1934 :1978 :clr]
            "pkg"     [:ccljx :pkg]})

