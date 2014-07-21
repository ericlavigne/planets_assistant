(ns vgap.core-test
  (:require [clojure.test :refer :all]
            [vgap.core :refer :all]))

(def homeworld
     {"id" 24, "name" "Kaye's World", "temp" 50, "ownerid" 6, "x" 1845, "y" 1524,

      "clans" 26250, "colchange" 0, "colonisttaxrate" 0, "colonisthappypoints" 100, "colhappychange" 7,

      "nativeclans" 0, "nativechange" 0, "nativetaxrate" 0, "nativehappypoints" 85, "nativehappychange" 4,
      "nativeracename" "none", "nativetype" 0,
      "nativegovernment" 0, "nativegovernmentname" "?", "nativetaxvalue" 20,

      "factories" 100, "mines" 100, "defense" 20,
      "builtfactories" 0, "builtmines" 0, "builtdefense" 0,
      "targetfactories" 0, "targetmines" 0, "targetdefense" 0,

      "supplies" 650, "suppliessold" 0, "checksupplies" 870,
      "megacredits" 6935, "checkmegacredits" 15635,

      "neutronium" 450,"duranium" 395, "tritanium" 663, "molybdenum" 554,
      "groundneutronium" 10949, "groundduranium" 2814, "groundtritanium" 2435, "groundmolybdenum" 1451,
      "densityneutronium" 50, "densityduranium" 15, "densitytritanium" 20, "densitymolybdenum" 95,
      "checkneutronium" 550, "checkduranium" 495, "checktritanium" 723, "checkmolybdenum" 594,
      "totalneutronium" 0, "totalduranium" 0, "totaltritanium" 0, "totalmolybdenum" 0,

      "friendlycode" "100", "flag" 1, "readystatus" 0, "buildingstarbase" false,

      "debrisdisk" 0, "infoturn" 2,
      "img" "http://library.vgaplanets.nu/planets/150.png"})

(deftest govern-homeworld
  (testing "Construction and taxes for first turn"
     (is (= 361 (planet-max-mines homeworld)))
     (is (= 261 (planet-max-factories homeworld)))
     (is (= 211 (planet-max-defense homeworld)))
  ))

