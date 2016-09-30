(ns cosycat.front.components.edit-user-modal
    (:require [re-frame.core :as re-frame]
              [reagent.core :as reagent]
              [react-bootstrap.components :as bs]
              [cosycat.utils :refer [human-time by-id]]
              [cosycat.app-utils :refer [dekeyword]]
              [taoensso.timbre :as timbre]))

(defn filter-kv [m pred]
  (reduce-kv (fn [acc k v]
               (if (pred v)
                 (assoc acc k v)
                 acc))
             {}
             m))

(defn spacer [] [:div.row {:style {:height "15px"}}])

(defn input-group [target val-map placeholder addon]
  (fn [target val-map placeholder addon]
    [:div.input-group
     [:span.input-group-addon addon]
     (let [id (str (dekeyword target) "-input")]
       [:input.form-control
        {:type "text"
         :id id
         :value (target @val-map)
         :placeholder placeholder
         :on-change #(swap! val-map assoc target (by-id id))}])]))

(defn edit-user [user val-map]
  (fn [{:keys [email firstname lastname]} val-map]
    [:div.container-fluid
     [:div.row [input-group :firstname val-map firstname [bs/glyphicon {:glyph "user"}]]]
     [spacer]
     [:div.row [input-group :lastname val-map lastname [bs/glyphicon {:glyph "user"}]]]
     [spacer]
     [:div.row [input-group :email val-map email "@"]]]))

(defn edit-user-modal [user show?]
  (let [val-map (reagent/atom {:email "" :firstname "" :lastname ""})]
    (fn [user show?]
      [bs/modal
       {:show @show?
        :onHide #(reset! show? false)}
       [bs/modal-header
        {:closeButton true}
        [bs/modal-title "Edit user profile"]]
       [bs/modal-body
        [:div.container-fluid
         [:div.row
          [edit-user user val-map]]
         [:div.row {:style {:height "25px"}}]
         [:div.row.text-right
          [bs/button
           {:onClick (fn [e]
                       (let [update-map (filter-kv @val-map #(not= % ""))]
                         (when-not (empty? update-map)
                           (do (re-frame/dispatch [:update-user-profile update-map])
                               (reset! show? false)))))}
           "Submit"]]]]])))

