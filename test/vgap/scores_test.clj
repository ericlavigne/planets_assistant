(ns vgap.scores-test
  (:require [clojure.test :refer :all]
            [vgap.scores :refer :all]
            [adi.core :as adi]
            [datomic.api :as datomic]
            [clj-time.core :as time]
))

(defn create-adi-test-db []
  (adi/connect! "datomic:mem://vgap" vgap-schema true true))

(deftest parsing-test
  (testing "Parse Nu-style datetime as date"
    (is (= (time/local-date 2014 9 23)
           (parse-datetime-as-date "9/23/2014 6:35:52 PM")))))

(deftest fetch-data-test
  (testing "Create a test database"
    (create-adi-test-db))
  (testing "Fetch events for NQ-PLS-70"
    (let [pls-events (fetch-game-events 100282)
          expected-events [{:type :join :player-num 6 :account-name "ericlavigne" :account-id 20214 :turn 1}
                           {:type :join :player-num 2 :account-name "battle toast" :account-id 15059 :turn 43}
                           {:type :resign :player-num 1 :account-name "macros the black" :account-id 18615 :turn 101}
                           {:type :drop :player-num 4 :account-name "kokunai" :account-id 4784 :turn 32}
                           {:type :dead :player-num 9 :turn 41}
                           {:type :create :date (time/local-date 2014 5 26)}
                           {:type :start :date (time/local-date 2014 6 18)}
                           {:type :win :turn 103 :date (time/local-date 2015 3 22) :account-name "yahoud"}
                           ]]
       (doseq [e expected-events]
         (is (some #{e} pls-events)))))
  (testing "Fetch events for team of 3 game (3 winners)"
    (let [team-of-3-events (fetch-game-events 115889)
          expected-events [{:type :win :turn 67 :date (time/local-date 2015 2 21) :account-name "vtapias"}
                           {:type :win :turn 67 :date (time/local-date 2015 2 21) :account-name "twogunsbob"}
                           {:type :win :turn 67 :date (time/local-date 2015 2 21) :account-name "chucky5"}
                          ]]
       (doseq [e expected-events]
         (is (some #{e} team-of-3-events)))))
)

