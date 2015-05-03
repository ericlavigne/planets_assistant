(ns vgap.scores
  (:require [adi.core :as adi]
            [datomic.api :as datomic]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [clj-time.core :as time]
            [clj-time.format :as timef]
            [clj-time.coerce :as timec]
))

(def vgap-schema {
                  :game {:nuid    [{:type :long
                                    :required true
                                    :unique :identity}]
                         :create-date [{:type :instant
                                        :required true}]
                         :start-date [{:type :instant}]
                         :end-date [{:type :instant}]
                         :name [{:required true
                                 :index true}]
                         :description [{:required true}]
                         }
                  :player {:game [{:type :ref
                                   :required true
                                   :ref {:ns :game}}]
                           :race [{:type :string}]
                           :num  [{:type :long
                                   :required true}]}
                  :scoreboard {:player     [{:type :ref
                                             :required true
                                             :ref {:ns :player}}]
                               :turn_num   [{:type :long
                                             :required true}]
                               :planets    [{:type :long
                                             :required true}]
                               :bases      [{:type :long
                                             :required true}]
                               :warships   [{:type :long
                                             :required true}]
                               :freighters [{:type :long
                                             :required true}]
                               :military   [{:type :long
                                             :required true}]
                               :score      [{:type :long
                                             :required true}]
                               :priority   [{:type :long
                                             :required true}]}
                  :account {:name [{:required true
                                    :unique :identity}]
                            :nuid [{:type :long
                                    :unique :value}]}
                  :event {:type    [{:type :enum
                                     :required true
                                     :enum {:ns :event.type
                                            :values #{:join :resign :drop :dead :win :finish}}}]
                          :player   [{:type :ref
                                      :required :true
                                      :ref {:ns :player}}]
                          :account  [{:type :ref
                                      :ref {:ns :account}}]
                          :date     [{:type :instant
                                      :required true}]
                          :turn_num [{:type :long}]}
                 })

(def adi-ds (adi/connect! "datomic:dev://localhost:4334/vgap" vgap-schema false true))

(def nu-datetime-format (timef/formatter "MM/dd/YYYY h:mm:ss a"))

(defn parse-datetime-as-date [datetime-string]
  "Parse Nu-style datetime like 9/23/2014 6:35:52 PM and return date (without time)"
  (.toLocalDate (timef/parse nu-datetime-format datetime-string)))

(defn string-replace
  "Like clojure.string/replace but supports multiple regex/replacement pairs"
  [string regex replacement & others]
    (let [string (clojure.string/replace string regex replacement)]
      (if (empty? others)
          string
          (apply string-replace string (first others) (second others) (drop 2 others)))))

(defn fetch-game-events [gameid]
  (let [events (get (json/read-str
                      (:body (client/get "http://api.planets.nu/game/loadevents"
                                         {:query-params {"gameid" gameid}})))
                    "events" :api-no-events)
        joins (filter #(= 3 (% "eventtype")) events)
        processed-joins (map (fn [evt] {:type :join :turn (evt "turn")
                                        :account-id (evt "accountid") :player-num (evt "playerid")
                                        :account-name (clojure.string/replace (evt "description")
                                                                              #" has joined.*" "")})
                             joins)
        resigns (filter #(= 8 (% "eventtype")) events)
        processed-resigns (map (fn [evt] {:type :resign :turn (evt "turn")
                                          :account-id (evt "accountid") :player-num (evt "playerid")
                                          :account-name (clojure.string/replace (evt "description")
                                                                                #" has resigned.*" "")})
                               resigns)
        drops (filter #(= 10 (% "eventtype")) events)
        processed-drops (map (fn [evt] {:type :drop :turn (evt "turn")
                                        :account-id (evt "accountid") :player-num (evt "playerid")
                                        :account-name (clojure.string/replace (evt "description")
                                                                                #" has been dropped.*" "")})
                             drops)
        deads (filter #(= 7 (% "eventtype")) events)
        processed-deads (map (fn [evt] {:type :dead :turn (evt "turn")
                                        :player-num (evt "playerid")})
                             deads)
        creates (filter #(= 1 (% "eventtype")) events)
        processed-creates (map (fn [evt] {:type :create :date (parse-datetime-as-date (evt "dateadded"))})
                               creates)
        starts (filter #(= 2 (% "eventtype")) events)
        processed-starts (map (fn [evt] {:type :start :date (parse-datetime-as-date (evt "dateadded"))})
                              starts)
        wins (filter #(= 6 (% "eventtype")) events)
        processed-wins (mapcat (fn [evt]
                                 (let [account-names (.split (clojure.string/replace
                                                               (clojure.string/replace
                                                                 (clojure.string/replace
                                                                   (evt "description")
                                                                   "+" " ")
                                                                 #" ha[sve]+ won.*" "")
                                                               "@" "")
                                                             " and ")]
                                   (map (fn [name] {:type :win :turn (evt "turn") :account-name name
                                                    :date (parse-datetime-as-date (evt "dateadded"))})
                                        account-names)))
                               wins)
        almost-wins (filter #(= 5 (% "eventtype")) events)
        uncategorized (clojure.set/difference (set events)
                                              (set (concat joins resigns drops deads creates starts wins almost-wins)))
        ]
    (concat processed-joins processed-resigns processed-drops
            processed-deads processed-creates processed-starts
            processed-wins
            uncategorized)))

(defn fetch-game-scores [gameid]
  (let [api-scores (get (json/read-str
                          (:body (client/get "http://api.planets.nu/account/loadscores"
                                             {:query-params {"gameid" gameid}})))
                        "scores" :api-no-scores)
        players (map-indexed (fn [i p]
                               (let [race (string-replace p #" \(.*" "")
                                     name (string-replace p #".*\(" "" #"\)" "")
                                     status (case name "dead" :dead "open" :open :live)]
                                 (merge (if (= status :live) {:account-name name} {})
                                        {:player-num (inc i) :race race :status status})))
                             (drop 1 (first (get api-scores "planets"))))
        turn-to-nested-planets (group-by first (drop 1 (api-scores "planets")))
        turn-to-nested-military (group-by first (drop 1 (api-scores "military")))
        scores (mapcat (fn [turn]
                         (let [planet-scores (drop 1 (first (get turn-to-nested-planets turn)))
                               military-scores (drop 1 (first (get turn-to-nested-military turn)))]
                           (map (fn [i planet-score military-score]
                                  {:turn turn :player-num (inc i) :planets planet-score :military military-score})
                                (range (count planet-scores))
                                planet-scores
                                military-scores)))
                       (keys turn-to-nested-planets))
        ]
    {:raw api-scores :players players :scores scores}))

(defn fetch-rated-games []
  (let [api-games (json/read-str
                          (:body (client/get "http://api.planets.nu/games/list?status=2,3"
                                             {:query-params {"status" "2,3"}})))
        games (map (fn [g]
                     (let [end-date (if (g "dateended") (parse-datetime-as-date (g "dateended")))
                           create-date (parse-datetime-as-date (g "datecreated"))]
                       (merge {:name (g "name") :description (g "description")
                               :created create-date
                               :game-id (g "id")}
                              (if (and end-date (not (.isBefore end-date create-date)))
                                  {:ended end-date}
                                  {}))))
                   api-games)
        rated-games (remove (fn [g] (re-find #"mentor"
                                             (.toLowerCase (:description g))))
                            games)
        ]
    rated-games))

(defn import-rated-game-list [ds]
  (let [games (map (fn [g]
                     {:game (merge (if (:ended g) {:end-date (timec/to-date (:ended g))} {})
                                   {:nuid (:game-id g)
                                    :create-date (timec/to-date (:created g))
                                    :name (:name g)
                                    :description (:description g)})})
                   (fetch-rated-games))]
    (adi/insert! ds (vec games))))

(defn load-game-list [ds]
  "Loads game records from database"
  (adi/select ds :game))

(defn load-game [ds nuid]
  "Loads specific game record from database by Nu game ID"
  (adi/select ds {:game {:nuid nuid}} :first true))

