(ns vgap.core
  (:require [clj-http.client :as client]
            [clojure.data.json :as json])
  (:use [clojure.string :only (join split)])
  (:gen-class))

(defn authenticate [username password]
  "Converts user/pass into apikey"
  (let [resp-raw (client/post "http://api.planets.nu/login"
                              {:query-params {"username" username
                                              "password" password}})
        resp (json/read-str (:body resp-raw))]
     (if (resp "success")
         (resp "apikey")
         :login-fail)))

(defn user-games [username]
  (mapcat #(json/read-str
             (:body (client/get "http://api.planets.nu/games/list"
                                {:query-params {"username" username
                                                "scope" %}})))
          ["0" "1"]))

(defn ask [q]
  "Ask a question, return an answer"
  (println (str q ":"))
  (read-line))

(defn print-grid [rows cols]
  (println (join " - " cols))
  (doseq [row rows]
    (println (join " - " (map row cols)))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [u (ask "username")
        p (ask "password")
        apikey (authenticate u p)]
    (if (= apikey :login-fail)
        (println "Login failed.")
        (do (println "apikey is" apikey)
            (print-grid (user-games u)
                        ["id" "name" "turn" "timetohost"])
            (println "Bye!")))))

