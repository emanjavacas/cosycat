(ns cosycat.admin.components.log
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [ajax.core :refer [GET]]
            [react-bootstrap.components :as bs]
            [cosycat.localstorage :refer [dump-db fetch-last-dump get-backups]]
            [taoensso.timbre :as timbre]))

(defn get-more-log [data-atom current & {:keys [plus] :or {plus 20}}]
  (GET "/admin/log"
       {:params {:from @current :to (+ @current plus)}
        :handler #(do (swap! current + (count (:lines %))) (swap! data-atom conj (:lines %)))
        :error-handler #(re-frame/dispatch [:notify {:message "Couldn't retrieve more"}])}))

(defn retrieve-more-btn [data-atom current]
  [bs/button {:onClick #(get-more-log data-atom current)}
   [bs/glyphicon {:glyph "plus"}]])

(defn log-frame []
  (let [data-atom (reagent/atom [])
        current (reagent/atom 0)]
    [:div.container-fluid     
     [:div.row
      [:div.col-lg-12
       [bs/panel
        [:div.container-fluid
         [:div.row [retrieve-more-btn data-atom current]]]]]]]))
