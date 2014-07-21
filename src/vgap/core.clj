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

(defn game-loadturn [game-id apikey]
  (json/read-str
    (:body (client/get "http://api.planets.nu/game/loadturn"
                       {:query-params {"gameid" game-id "apikey" apikey}}))))

(defn game-loadrst [game-id apikey]
  ((game-loadturn game-id apikey) "rst"))

(defn ask [q]
  "Ask a question, return an answer"
  (println (str q ":"))
  (read-line))

(defn print-grid [rows cols]
  (println (join " - " cols))
  (doseq [row rows]
    (println (join " - " (map row cols)))))

(defn rst-myplanets [rst]
  "Extract my planets from rst"
  (let [my-id (get-in rst ["player" "id"])]
     (filter #(= my-id (% "ownerid")) (rst "planets"))))

(defn clans-max-mines [clans]
   (if (<= clans 200) clans
       (Math/round (Math/floor (+ 200 (Math/sqrt (- clans 200)))))))
(defn clans-max-factories [clans]
   (if (<= clans 100) clans
       (Math/round (Math/floor (+ 100 (Math/sqrt (- clans 100)))))))
(defn clans-max-defense [clans]
   (if (<= clans 50) clans
       (Math/round (Math/floor (+ 50 (Math/sqrt (- clans 50)))))))

(defn planet-max-mines [planet]
  (clans-max-mines (planet "clans")))
(defn planet-max-factories [planet]
  (clans-max-factories (planet "clans")))
(defn planet-max-defense [planet]
  (clans-max-defense (planet "clans")))

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
            (let [gameid (ask "Choose a game")
                  turn (game-loadturn gameid apikey)
                  rst (turn "rst")]
              (println "In game" ((rst "game") "name")
                       "you are playing race" ((rst "player") "raceid"))
              (print-grid (rst-myplanets rst) ["id" "name"])
              (print-grid (rst-myplanets rst) (keys (first (rst-myplanets rst))))
            )
            (println "Bye!")))))

