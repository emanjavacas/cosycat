(ns cleebo.updates.components.event-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.app-utils :refer [dekeyword]]
            [cleebo.utils :refer [human-time]]
            [cleebo.components :refer [user-thumb]]
            [taoensso.timbre :as timbre]))

(defn denormalize-annotation [anns]
  (let [anns-by-token-id (vals anns)
        anns-by-ann-id (vals anns-by-token-id)]
    (if (= 1 (count anns-by-token-id))
      (if (= 1 (count anns-by-ann-id))
        (first (vals anns-by-ann-id))   ;single ann single token-id
        (vals anns-by-ann-id))          ;multiple anns single token-id
      '() ;multiple token-ids
      )))

(defn extract-ann-data [anns]
  (first (first (apply hash-set (vals anns)))))

(defn event-source [source-user]
  (let [avatar (re-frame/subscribe [:user source-user :avatar :href])]
    (fn [source-user]
      [:div {:style {:margin "10px"}} [user-thumb (or @avatar "img/avatars/server.png")]])))

(defn timestamp-text [timestamp]
  [:span.text-muted
   {:style {:font-size "15px"
            :margin-left "7px"}}
   (human-time timestamp)])

(defn event-item [{:keys [header-text source-user event-text timestamp] :as args}]
  [bs/list-group-item
   (reagent/as-component
    [:div.container-fluid
     [:div.row
      [:div.col-sm-10
       [:div.container-fluid
        [:div.row
         [:h4 header-text (timestamp-text timestamp)]]
        [:div.row [:span event-text]]]]
      [:div.col-sm-2.text-right [event-source source-user]]]])])

(defmulti event-component (fn [{event-type :type}] event-type))

(defmethod event-component :annotation
  [{{:keys [anns project hit-id]} :payload event-type :type timestamp :received}]
  (fn [{{:keys [anns project hit-id]} :payload event-type :type timestamp :received}]
    (let [[ann-key {:keys [_version username] :as ann-data}] (extract-ann-data anns)]
      [event-item {:header-text (dekeyword event-type)
                   :source-user username
                   :event-text (str ann-data)
                   :timestamp timestamp}])))

(defmethod event-component :default
  [{payload :payload event-type :type timestamp :received}]
  (fn [{payload :payload event-type :type timestamp :received}]
    [event-item {:header-text (dekeyword event-type)
                 :source-user ""
                 :event-text (str payload)
                 :timestamp timestamp}]))



