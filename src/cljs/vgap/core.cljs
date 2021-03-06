(ns vgap.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [goog.history.EventType :as EventType]
              [ajax.core :refer [GET POST]])
    (:import goog.History))

(def user (atom {}))

;; -------------------------
;; Components

 (defn textfield [state]
   [:input {:type "text" :value @state
            :on-change #(reset! state (-> % .-target .-value))}])

;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Welcome to vgap " (:username @user)]
   [:div [:a {:href "#" :on-click #(reset! user {})} "Logout"]]])

(defn login-handler [username password res]
  (.log js/console (str res))
  (if (:success res)
      (reset! user
              {:username username :password password
               :api-key (:api-key res)
               :logged-in true})
      (do (reset! username "")
          (reset! password ""))))

(defn login-page []
  (let [username (atom "")
        password (atom "")]
    (fn []
      [:div [:h2 "Login"]
            [:span "Username"]
            [textfield username]
            [:br]
            [:span "Password"]
            [textfield password]
            [:br]
            [:input {:type "button" :value "Log In"
                     :on-click (fn []
                                 (let [u @username
                                       p @password]
                                   (GET "/login"
                                         {:params {"username" u
                                                   "password" p}
                                          :response-format :json
                                          :keywords? true
                                          :handler #(login-handler u p %)
                                          :error-handler
                                          (fn [{:keys [status status-text]}]
                                            (.log js/console
                                                  (str "something bad happened: " status
                                                       " " status-text)))
                                          })))}]
])))

(defn scoreboard []
  (let [ratings (atom [])
        updater (fn [] (GET "/ratings"
                            :params {}
                            :response-format :json
                            :handler #(reset! ratings %)
                            :error-handler (fn [{:keys [status status-text]}]
                                             (.log js/console
                                                   (str "something bad happened: " status
                                                        " " status-text)))))]
    (fn []
      (updater)
      (js/setTimeout updater 15000)
      [:div
        [:h1 "Scoreboard"]
        [:table [:tr [:th "Account"] [:th "Rating"]]
                (for [r @ratings]
                  [:tr [:td (r "account-name")] [:td (r "rating")]])]])))

(defn main-page []
  [:div
    [scoreboard]
    (if (:username @user)
        [home-page]
        [login-page])])

;; -------------------------
;; Initialize app
(defn init! []
  (reagent/render-component [main-page] (.getElementById js/document "app")))

;; -------------------------
;; History
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))
;; need to run this after routes have been defined
(hook-browser-navigation!)
