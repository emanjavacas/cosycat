(ns cleebo.error.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]))

(defn error-panel []
  (let [error-data (re-frame/subscribe [:session :session-error])]
    (fn []
      (let [{:keys [error message]} @error-data]
        [:div.container
         [:div.row
          [:div.jumbotron
           [:div.container-fluid
            [:div.row [:h2 "Oooops! Something bad happened"]]
            [:div.row {:style {:height "50px"}}]
            [:div.row
             [:p            
              error
              [:span.text-muted
               {:style {:padding "50px"}}
               message]]]]]]]))))

