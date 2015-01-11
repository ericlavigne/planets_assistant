(ns vgap.handler
  (:require [vgap.dev :refer [browser-repl start-figwheel]]
            [vgap.core :as vgap]
            [compojure.core :refer [GET ANY defroutes]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [selmer.parser :refer [render-file]]
            [environ.core :refer [env]]
            [clojure.data.json :as json]
            [prone.middleware :refer [wrap-exceptions]]))

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json; charset=utf-8"}
   :body (json/write-str data)})

(defroutes routes
  (GET "/" [] (render-file "templates/index.html" {:dev (env :dev?)}))
  (ANY "/login" [username password]
       (let [api-key (vgap/authenticate username password)]
         (json-response
           (if (or (nil? api-key) (= api-key :login-fail))
               {:success false}
               {:success true :username username :password password :api-key api-key}))))
  (resources "/")
  (not-found "Not found"))

(def app
  (let [handler (wrap-defaults routes site-defaults)]
    (if (env :dev?) (wrap-exceptions handler) handler)))
