(ns vgap.scores
  (:require [adi.core :as adi]
            [datomic.api :as datomic]))

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

(def adi-db (adi/connect! "datomic:mem://vgap" vgap-schema true true))


