(defproject msd-languages "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://github.com/belovehq/msd-languages"
  :license {:name "MIT License"
            :url  "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/java.jdbc "0.7.5"]
                 [com.h2database/h2 "1.4.195"]
                 [cld "0.1.0"]
                 [com.layerware/hugsql "0.4.8"]
                 [com.mathworks/engine "R2017A"]]

  :resource-paths ["resources"]
  :source-paths ["src"]
  :target-path "target/%s"

  :main ^:skip-aot msdlang.clustering

  :profiles {:uberjar {:aot :all}})
