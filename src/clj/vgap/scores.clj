(ns vgap.scores
  (:require [adi.core :as adi]
            [datomic.api :as datomic]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [clj-time.core :as time]
            [clj-time.format :as timef]
            [clj-time.coerce :as timec]
            [medley.core :as m]
            [clojure.java.io :as io]
            [vgap.core :as core]
            [aws.sdk.s3 :as s3]
  )
  (:import java.io.FileInputStream
           java.util.zip.ZipInputStream))

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
                  :player {:key-game-num [{:required true :unique :identity}]
                           :game [{:type :ref
                                   :required true
                                   :ref {:ns :game}}]
                           :num  [{:type :long
                                   :required true
                                   :restrict ["player-num of 0 is placeholder, not real player-num"
                                              #(> % 0)]}]
                           :race [{:type :string}]}
                  :scoreboard {:key-game-player-turn [{:required true :unique :identity}]
                               :player     [{:type :ref
                                             :required true
                                             :ref {:ns :player}}]
                               :turn-num   [{:type :long
                                             :required true}]
                               :planets    [{:type :long
                                             :required true}]
                               :military   [{:type :long
                                             :required true}]}
                  :account {:name [{:required true
                                    :unique :identity}]
                            :nuid [{:type :long
                                    :unique :value
                                    :restrict ["account-id of 0 is placeholder, not real ID"
                                               #(> % 0)]}]}
                  :event {:key-type-game-player-turn-account [{:required true :unique :identity}]
                          :type    [{:type :enum
                                     :required true
                                     :enum {:ns :event.type
                                            :values #{:join :resign :drop :dead :win :finish}}}]
                          :player   [{:type :ref
                                      :required :true
                                      :ref {:ns :player}}]
                          :account  [{:type :ref
                                      :ref {:ns :account}}]
                          :turn-num [{:type :long
                                      :required true}]
                          :date     [{:type :instant}]}
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

(defn merge-list-by [f list]
  (vec (map #(apply merge %)
            (vals (group-by f list)))))

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
                                   (map (fn [name] (if (= name "Game ended.")
                                                       {:type :end :turn (evt "turn")}
                                                       {:type :win :turn (evt "turn") :account-name name
                                                        :date (parse-datetime-as-date (evt "dateadded"))}))
                                        account-names)))
                               wins)
        almost-wins (filter #(= 5 (% "eventtype")) events)
        uncategorized (clojure.set/difference (set events)
                                              (set (concat joins resigns drops deads creates starts wins almost-wins)))
        ]
    (vec
      (map (comp #(if (= 0 (:player-num %))
                      (dissoc % :player-num)
                      %)
                 #(if (:account-name %)
                      (assoc % :account-name (.toLowerCase (:account-name %)))
                      %)
           )
           (remove #(#{"dead" "open"} (:account-name %))
             (concat processed-joins processed-resigns processed-drops
                     processed-deads processed-creates processed-starts
                     processed-wins
                     uncategorized))))))

(defn fetch-game-scores [gameid]
  (let [api-scores (get (json/read-str
                          (:body (client/get "http://api.planets.nu/account/loadscores"
                                             {:query-params {"gameid" gameid}})))
                        "scores" :api-no-scores)
        players (map-indexed (fn [i p]
                               (let [race (string-replace p #" \(.*" "")
                                     name (.toLowerCase (string-replace p #".*\(" "" #"\)" ""))
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

(defn game-entity-id [ds nuid]
  (let [rec (adi/select ds {:game {:nuid nuid}} :first true :ids true)]
    (:id (:db rec))))

(defn import-game-details [ds game-nuid]
  "Imports players, scores, and events for specified game. Game should already be in the datasource."
  (let [_ (println (str "\nimport game details for game: " game-nuid))
        api-events (fetch-game-events game-nuid)
        _ (println (str "\napi-events: " api-events))
        api-scores (fetch-game-scores game-nuid)
        game-id (game-entity-id ds game-nuid)
        _ (if (nil? game-id) (throw (Exception. (str "Game not found: " game-nuid))))
        players (adi/insert! ds (vec (map (fn [p] {:player {:key-game-num (str game-nuid "-" (:player-num p))
                                                            :game game-id
                                                            :num (:player-num p)
                                                            :race (:race p)}})
                                          (:players api-scores))))
        pnum-to-id (into {}
                         (map (fn [p] [(:num (:player p))
                                       (:id (:db p))])
                              players))
        scores (adi/insert! ds (vec (map (fn [s] {:scoreboard {:key-game-player-turn (str game-nuid "-" (:player-num s) "-" (:turn s))
                                                               :player (pnum-to-id (:player-num s))
                                                               :turn-num (:turn s)
                                                               :planets (:planets s)
                                                               :military (:military s)}})
                                         (:scores api-scores))))
        accounts-from-events (let [event-accounts (vec (m/distinct-by #(:name (:account %))
                                                                      (map (fn [e] {:account (merge {:name (:account-name e)}
                                                                                                    (if (and (:account-id e) (< 0 (:account-id e)))
                                                                                                        {:nuid (e :account-id)}
                                                                                                        {}))})
                                                                           (filter #(#{:join :resign :drop :win} (:type %))
                                                                                   api-events))))]
                               (println (str "\nevent-accounts: " event-accounts))
                               (adi/insert! ds event-accounts))
        _ (println (str "\naccounts-from-events: " accounts-from-events))
        accounts-from-scores (adi/insert! ds (vec (map (fn [p] {:account {:name (:account-name p)}})
                                                       (filter :account-name (:players api-scores)))))
        _ (println (str "\naccounts-from-scores: " accounts-from-scores))
        account-name-to-id (merge (into {} (map (fn [a] [(:name (:account a))
                                                         (:id (:db a))])
                                                accounts-from-events))
                                  (into {} (map (fn [a] [(:name (:account a))
                                                         (:id (:db a))])
                                                accounts-from-scores)))
        _ (println (str "\naccount-name-to-id: " account-name-to-id))
        dead-events (adi/insert! ds (vec (filter #(:player (:event %))
                                                 (map (fn [e] {:event {:key-type-game-player-turn-account
                                                                             (str ":dead-" game-nuid "-" (:player-num e) "-" (:turn e) "-")
                                                                       :type :dead
                                                                       :player (pnum-to-id (:player-num e))
                                                                       :turn-num (:turn e)}})
                                                      (filter #(= :dead (:type %)) api-events)))))
        account-events (let [account-api-events (filter #(#{:join :resign :drop :win} (:type %)) api-events)
                             _ (println (str "\naccount-api-events: " (vec account-api-events)))
                             events-for-insertion
                               (filter #(:player (:event %))
                                       (map (fn [e]
                                              (let [pnum (or (:player-num e)
                                                             (first (map :player-num
                                                                         (filter #(= (:account-name e)
                                                                                     (:account-name %))
                                                                                 (:players api-scores)))))]
                                                {:event (merge {:key-type-game-player-turn-account
		                                                      (str (:type e) "-" game-nuid "-" (:player-num e)
		                                                           "-" (:turn e) "-" (:account-name e))
		                                                :type (:type e)
		                                                :player (pnum-to-id pnum)
		                                                :account (account-name-to-id (:account-name e))
		                                                :turn-num (:turn e)}
		                                               (if (:date e) {:date (timec/to-date (:date e))} {}))}))
		                            account-api-events))]
                         (println (str "\nevents-for-insertion: " (vec events-for-insertion)))
		         (adi/insert! ds (vec events-for-insertion)))
        finish (first (filter #(#{:win :end} (:type %)) api-events))
        finish-events (if finish (adi/insert! ds (vec (map (fn [p] {:event {:key-type-game-player-turn-account
                                                                                 (str ":finish-" game-nuid "-" (:player-num p)
                                                                                      "-" (:turn finish) "-" (:account-name p))
                                                                            :type :finish
                                                                            :player (pnum-to-id (:player-num p))
                                                                            :account (account-name-to-id (:account-name p))
                                                                            :turn-num (:turn finish)}})
                                                           (filter :account-name (:players api-scores))))))
        start (first (filter #(= :start (:type %)) api-events))
        _ (if start (adi/update! ds {:game/nuid game-nuid} {:game/start-date (timec/to-date (:date start))}))
        ]
    nil))

(defn import-all-game-details [ds]
  (doseq [g (shuffle (import-rated-game-list ds))]
    (let [game-id (:nuid (:game g))]
      (println "Importing game " game-id)
      (import-game-details ds game-id)))
  nil)

(defn find-join-events [ds]
  (datomic/q '[:find ?account-name ?game-nuid :where
               [?e :event/type :event.type/join]
               [?e :event/player ?p]
               [?p :player/game ?g]
               [?g :game/nuid ?game-nuid]
               [?e :event/account ?a]
               [?a :account/name ?account-name]]
             (datomic/db (:connection ds))))

(defn find-win-events [ds]
  (datomic/q '[:find ?account-name ?game-nuid :where
               [?e :event/type :event.type/win]
               [?e :event/player ?p]
               [?p :player/game ?g]
               [?g :game/nuid ?game-nuid]
               [?e :event/account ?a]
               [?a :account/name ?account-name]]
             (datomic/db (:connection ds))))

(defn calculate-ratings [ds]
  (let [win-events (find-win-events ds)
        join-events (find-join-events ds)
        rated-games (clojure.set/intersection (set (map second win-events))
                                              (set (map second join-events)))
        rated-joins-by-player (group-by first (filter #(rated-games (second %)) join-events))
        rated-wins-by-player (group-by first (filter #(rated-games (second %)) win-events))
        rated-players (keys (merge rated-joins-by-player rated-wins-by-player))]
    (reverse (sort-by :rating
                      (map (fn [p] (let [participated (count (set (concat (get rated-joins-by-player p [])
                                                                          (get rated-wins-by-player p []))))
                                         won (count (get rated-wins-by-player p []))]
                                     {:account-name p :rating (Math/round (/ (* 100.0 won) (inc participated)))}))
                           rated-players)))))

(defn fetch-game-full-from-nu [gameid]
  (let [req (client/get "http://api.planets.nu/game/loadall"
                        {:query-params {"gameid" gameid}
                         :as :byte-array})
        _ (assert (= 200 (:status req)) (str "Status: " (:status req)))
        tmp-file (java.io.File/createTempFile (str "game-" gameid "-") ".zip")]
    (with-open [w (io/output-stream tmp-file)]
      (.write w (:body req)))
    tmp-file))

(defn transfer-game-full-nu-to-s3
  ([gameid]
     (transfer-game-full-nu-to-s3 gameid {:access-key (core/setting :aws-access-key) :secret-key (core/setting :aws-secret-key)}))
  ([gameid creds]
     (let [game-from-nu (fetch-game-full-from-nu gameid)]
       (s3/put-multipart-object creds "vgap" (str "game/loadall/" gameid ".zip") game-from-nu)
       (.delete game-from-nu))
       nil))

(defn fetch-game-full-from-s3
  ([gameid]
     (fetch-game-full-from-s3 gameid {:access-key (core/setting :aws-access-key) :secret-key (core/setting :aws-secret-key)}))
  ([gameid creds]
     (let [s3-res (s3/get-object creds "vgap" (str "game/loadall/" gameid ".zip"))
           s3-bytes (org.apache.commons.io.IOUtils/toByteArray (:content s3-res))
           tmp-file (java.io.File/createTempFile (str "game-" gameid "-") ".zip")]
       (with-open [w (io/output-stream tmp-file)]
         (.write w s3-bytes))
       tmp-file)))

; http://www.thecoderscorner.com/team-blog/java-and-jvm/12-reading-a-zip-file-from-java-using-zipinputstream
;
; (first (zip-file-map fetched-game (fn [s] (apply str (take 50 s)))))
;
;   => "{\"settings\": {\"name\":\"Nercodonia Sector\",\"turn\":0,"
;
(defn zip-file-map [file fun]
  (let [buffer (byte-array 2048)
        fis (FileInputStream. file)
        zis (ZipInputStream. fis)
        res (loop [zip-entry (.getNextEntry zis)
                   zip-entry-pieces []
                   results []]
              (if (nil? zip-entry)
                  results
                  (let [len (.read zis buffer)]
                    (if (> len 0)
                        (recur zip-entry
                               (conj zip-entry-pieces (String. buffer 0 len))
                               results)
                        (recur (.getNextEntry zis)
                               []
                               (conj results (fun (apply str zip-entry-pieces))))))))]
    (.close zis)
    res))

