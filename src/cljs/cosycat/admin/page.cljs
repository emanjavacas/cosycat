(ns cosycat.admin.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.app-utils :refer [dekeyword]]
            [cosycat.tree :refer [data-tree]]
            [cosycat.admin.components.db :refer [db-frame]]
            [cosycat.admin.components.log :refer [log-frame]]
            [taoensso.timbre :as timbre]))

(defmulti admin-frame identity)

(defmethod admin-frame :db [] [#'db-frame])

(defmethod admin-frame :log [] [#'log-frame])

(defmethod admin-frame :tree []
  (let [db (re-frame/subscribe [:db])]
    [data-tree @db]))

(defmethod admin-frame :default []
  [:div.container-fluid
   [:div.row
    {:style {:margin-top "50px"}}
    [:div.col-lg-3]
    [:div.col-lg-6.text-center
     [:div [:h3 "Not implemented yet!"]]]
    [:div.col-lg-3]]])

(defn pill [key active-frame]
  (fn [key active-frame]
    [:li {:class (when (= key @active-frame) "active") :style {:cursor "pointer"}}
     [:a {:onClick #(reset! active-frame key)}
      (clojure.string/capitalize (dekeyword key))]]))

(defn admin-panel []
  (let [active-frame (reagent/atom :tree)]
    (fn []
      [:div.container-fluid
       [:div.row
        [:div.col-lg-12 [:h3 [:span.text-muted "Admin Panel"]]]]
       [:div.row [:div.col-lg-12 [:hr]]]
       [:div.container-fluid
        [:div.row
         [:div.col-lg-12
          [:ul.nav.nav-pills
           [pill :tree active-frame]
           [pill :log active-frame]
           [pill :db active-frame]]]]
        [:div.row {:style {:margin-top "20px"}}]
        [:div.row (admin-frame @active-frame)]]])))
