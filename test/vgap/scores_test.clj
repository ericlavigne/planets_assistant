(ns vgap.scores-test
  (:require [clojure.test :refer :all]
            [vgap.scores :refer :all]
            [adi.core :as adi]
            [datomic.api :as datomic]
))

(defn create-adi-test-db []
  (adi/connect! "datomic:mem://vgap" vgap-schema true true))

(deftest fetch-data-test
  (testing "Can create a test database"
    (create-adi-test-db)))

