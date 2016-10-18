(ns cosycat.updates.components.event-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.app-utils :refer [dekeyword]]
            [cosycat.utils :refer [human-time]]
            [cosycat.components :refer [user-thumb]]
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

(defn event-item [{:keys [header-text source-user event-child timestamp] :as args}]
  [bs/list-group-item
   (reagent/as-component
    [:div.container-fluid
     [:div.row
      [:div.col-sm-10
       [:div.container-fluid
        [:div.row
         [:h4 header-text (timestamp-text timestamp)]]
        [:div.row [:span event-child]]]]
      [:div.col-sm-2.text-right [event-source source-user]]]])])

(defmulti event-component (fn [{event-type :type}] (keyword event-type)))

(defmethod event-component :annotation
  [{{:keys [anns project hit-id]} :data event-type :type timestamp :timestamp}]
  (fn [{{:keys [anns project hit-id]} :data event-type :type timestamp :timestamp}]
    (let [[ann-key {:keys [_version username] :as ann-data}] (extract-ann-data anns)]
      [event-item {:header-text event-type
                   :source-user username
                   :event-child (str ann-data)
                   :timestamp timestamp}])))

(defmethod event-component :query
  [{{query-str :query-str corpus :corpus} :data event-type :type timestamp :timestamp}]
  (let [me (re-frame/subscribe [:me :username])]
    (fn [{{query-str :query-str corpus :corpus} :data event-type :type timestamp :timestamp}]
      [event-item {:header-text event-type
                   :source-user @me
                   :event-child [:div.container-fluid
                                 [:div.row [:span "Corpus: " [:code corpus]]]
                                 [:div.row [:span "Query string: " [:code query-str]]]]
                   :timestamp timestamp}])))

(defmethod event-component :default
  [{data :data event-type :type timestamp :timestamp}]
  (fn [{data :data event-type :type timestamp :timestamp}]
    [event-item {:header-text event-type
                 :source-user ""
                 :event-child (str data)
                 :timestamp timestamp}]))



