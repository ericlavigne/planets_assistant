(ns vgap.scores-test
  (:require [clojure.test :refer :all]
            [vgap.scores :refer :all]
            [adi.core :as adi]
            [datomic.api :as datomic]
            [clj-time.core :as time]
            [clj-time.coerce :as timec]
))

(defn create-adi-test-ds []
  (adi/connect! "datomic:mem://vgap" vgap-schema true true))

(deftest parsing-test
  (testing "Parse Nu-style datetime as date"
    (is (= (time/local-date 2014 9 23)
           (parse-datetime-as-date "9/23/2014 6:35:52 PM")))))

(deftest fetch-data-test
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
  (testing "Fetch events for two winners, one of which has space in name"
    (let [team-of-3-events (fetch-game-events 54512)
          expected-events [{:type :win :turn 129 :date (time/local-date 2014 4 12) :account-name "big beefer"}
                           {:type :win :turn 129 :date (time/local-date 2014 4 12) :account-name "lqdvnm"}
                          ]]
       (doseq [e expected-events]
         (is (some #{e} team-of-3-events)))))
  (testing "Fetch events for old game, in which win event doesn't specify winner."
    (let [rebirth-events (fetch-game-events 886)
          expected-events [{:type :end :turn 81}
                           {:type :create :date (time/local-date 2010 11 5)}
                          ]]
       (doseq [e expected-events]
         (is (some #{e} rebirth-events)))))
  (testing "Fetch scores for game with spaces in usernames and some open/dead players"
    (let [small-world-scores (fetch-game-scores 45277)
          expected-players [{:player-num 1 :race "The Feds" :status :live :account-name "gloria stitz"}]
          expected-scores [{:turn 48 :player-num 3 :planets 186 :military 473672}]]
      (doseq [tests-and-place [[expected-players :players] [expected-scores :scores]]]
        (doseq [test-case (get tests-and-place 0)]
          (is (some #{test-case}
                    (get small-world-scores
                         (get tests-and-place 1)
                         [(get tests-and-place 1)])))))))
)

(deftest import-test
  (testing "import games and load one of them"
    (let [ds (create-adi-test-ds)]
      (import-rated-game-list ds)
      (is (= (load-game ds 815)
             {:game {:name "Rebirth 1", :nuid 815,
                     :create-date (timec/to-date (time/local-date 2010 11 3))
                     :end-date (timec/to-date (time/local-date 2011 6 30))
                     :description "This is the first Alpha Test Game. Standard fixed race game running every day for the first month and 3 turns/week after. Game ends when one player achieves 65% military score.<br/><br/>Warning: This is an alpha game. We will encounter bugs and may even need to play a turn or two over again. Please do not join this game unless you are willing to come in with a light heart and an eye for improving the game. This game is for fun, an opportunity to be involved in the very first games to run on VGA Planets Nu."}}))))
  (testing "import details of some games"
    (let [ds (create-adi-test-ds)]
      (import-rated-game-list ds)
      (import-game-details ds 815)
      (import-game-details ds 32562)
      (import-game-details ds 41817)
      (import-game-details ds 79766)
))
)

