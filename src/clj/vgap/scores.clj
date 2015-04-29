(ns vgap.scores
  (:require [adi.core :as adi]
            [datomic.api :as datomic]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [clj-time.core :as time]
            [clj-time.format :as timef]
))

(def vgap-schema {
                  :game {:nuid    [{:type :long
                                    :required true
                                    :unique :value}]
                         :start-date [{:type :instant
                                       :required true}]
                         :winners [{:type :ref
                                    :cardinality :many
                                    :ref {:ns :player}}]
                         :players [{:type :ref
                                    :cardinality :many
                                    :ref {:ns :player}}]}
                  :player {:game   [{:type :ref
                                     :required true
                                     :ref {:ns :game}}]
                           :race   [{:type :string}]
                           :symbol [{:required true}]}
                  :turn {:game [{:type :ref
                                 :required true
                                 :ref {:ns :game}}]
                         :num  [{:type :long
                                 :required true}]
                         :date [{:type :instant
                                 :required true}]}
                  :scoreboard {:player     [{:type :ref
                                             :required true
                                             :ref {:ns :player}}]
                               :turn       [{:type :ref
                                             :required true
                                             :ref {:ns :turn}}]
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
                                    :unique :value}]}
                  :event {:type    [{:type :enum
                                     :required true
                                     :enum {:ns :event.type
                                            :values #{:join}}}]
                          :subject [{:type :ref
                                     :required :true}]
                          :object  [{:type :ref}]
                          :date    [{:type :instant
                                     :required true}]}
                 })

(def adi-db (adi/connect! "datomic:dev://localhost:4334/vgap" vgap-schema false true))

(def nu-datetime-format (timef/formatter "MM/dd/YYYY h:mm:ss a"))

(defn parse-datetime-as-date [datetime-string]
  "Parse Nu-style datetime like 9/23/2014 6:35:52 PM and return date (without time)"
  (.toLocalDate (timef/parse nu-datetime-format datetime-string)))

(defn user-games [username]
  (mapcat #(json/read-str
             (:body (client/get "http://api.planets.nu/games/list"
                                {:query-params {"username" username
                                                "scope" %}})))
          ["0" "1"]))

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

