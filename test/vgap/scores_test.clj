(ns vgap.scores-test
  (:require [clojure.test :refer :all]
            [vgap.scores :refer :all]
            [adi.core :as adi]
            [datomic.api :as datomic]
))

(defn create-adi-test-db []
  (adi/connect! "datomic:mem://vgap" vgap-schema true true))

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
                           ; {:type :win :player-num 10 :account-name "yahoud" :account-id 20989 :turn 103}
                           ]]
       (doseq [e expected-events]
         (is (some #{e} pls-events))))
))

