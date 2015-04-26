(defproject vgap "0.1.0-SNAPSHOT"
  :description "Tools for playing VGA Planets at http://planets.nu"
  :url "http://planets.nu"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main ^:skip-aot vgap.core

  :source-paths ["src/clj" "src/cljs"]

  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :username [:env/datomic_username]
                                   :password [:env/datomic_download_key]}}

  :dependencies [[org.clojure/core.cache "0.6.3"]
                 [org.clojure/clojure "1.6.0"]
                 [im.chit/adi "0.3.1-SNAPSHOT"]
                 [org.apache.httpcomponents/httpclient "4.3.5" :exclusions [commons-logging]]
                 [com.datomic/datomic-pro "0.9.5153" :exclusions [joda-time]]
                 [clj-time "0.6.0"]
                 [clj-http "0.9.2"]
                 [org.clojure/data.json "0.2.5"]
                 [com.facebook/react "0.11.2"]
                 [reagent "0.4.3"]
                 [reagent-forms "0.3.9"]
                 [reagent-utils "0.1.2"]
                 [secretary "1.2.1"]
                 [org.clojure/clojurescript "0.0-2657" :scope "provided"]
                 [com.cemerick/piggieback "0.1.3"]
                 [weasel "0.4.2"]
                 [ring "1.3.2"]
                 [ring/ring-defaults "0.1.3"]
                 [prone "0.8.0"]
                 [compojure "1.3.1"]
                 [selmer "0.7.9"]
                 [environ "1.0.0"]
                 [leiningen "2.5.0"]
                 [figwheel "0.1.6-SNAPSHOT"]
                 [cljs-ajax "0.3.6"]]

  :plugins [
            [lein-cljsbuild "1.0.4"]
            [lein-environ "1.0.0"]
            [lein-ring "0.9.0"]
            [lein-asset-minifier "0.2.2"]]

  :ring {:handler vgap.handler/app
         :uberwar-name "vgap.war"}

  :min-lein-version "2.5.0"

  :uberjar-name "vgap.jar"

  :clean-targets ^{:protect false} ["resources/public/js"]

  :minify-assets
  {:assets
    {"resources/public/css/site.min.css" "resources/public/css/site.css"}}

  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "resources/public/js/app.js"
                                        :output-dir    "resources/public/js/out"
                                        :externs       ["react/externs/react.js"]
                                        :optimizations :none
                                        :pretty-print  true}}}}

  :test-selectors {:default (constantly true)
                   :browser :browser
                   :non-browser (complement :browser)}

  :profiles {:dev {:repl-options {:init-ns vgap.handler
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                   :dependencies [[ring-mock "0.1.5"]
                                  [ring/ring-devel "1.3.2"]
                                  [pjstadig/humane-test-output "0.6.0"]

                                  [clj-webdriver "0.6.0"
                                      :exclusions [org.seleniumhq.selenium/selenium-server
                                                   org.seleniumhq.selenium/selenium-java
                                                   org.seleniumhq.selenium/selenium-remote-driver]]
                                  [org.seleniumhq.selenium/selenium-server "2.45.0"]
                                  [org.seleniumhq.selenium/selenium-java "2.45.0"]
                                  [org.seleniumhq.selenium/selenium-remote-driver "2.45.0"]
                                 ]

                   :plugins [[lein-figwheel "0.2.0-SNAPSHOT"]]

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :figwheel {:http-server-root "public"
                              :server-port 3449
                              :css-dirs ["resources/public/css"]
                              :ring-handler vgap.handler/app}

                   :env {:dev? true}

                   :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]
                                              :compiler {:source-map true}}}}}

             :uberjar {:hooks [leiningen.cljsbuild minify-assets.plugin/hooks]
                       :env {:production true}
                       :aot :all
                       :omit-source true
                       :cljsbuild {:jar true
                                   :builds {:app
                                             {:source-paths ["env/prod/cljs"]
                                              :compiler
                                              {:optimizations :advanced
                                               :pretty-print false}}}}}

             :production {:ring {:open-browser? false
                                 :stacktraces?  false
                                 :auto-reload?  false}}})
