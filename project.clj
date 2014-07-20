(defproject vgap "0.1.0-SNAPSHOT"
  :description "Tools for playing VGA Planets at http://planets.nu"
  :url "http://planets.nu"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-http "0.9.2"]
                 [org.clojure/data.json "0.2.5"]]
  :main ^:skip-aot vgap.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

