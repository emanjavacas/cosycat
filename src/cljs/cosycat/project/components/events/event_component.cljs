(ns cosycat.project.components.events.event-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.app-utils :refer [dekeyword function?]]
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

(defn sort-repeated [timestamp repeated]
  (let [[last-timestamp & rest-timestamps] (->> (into repeated [timestamp]) (sort >))]
    {:last-timestamp last-timestamp
     :rest-timestamps rest-timestamps}))

(defn expand-button [collapsed?]
  (fn [collapsed?]
    [bs/button
     {:style {:width "30px" :height "30px" :line-height "30px" :padding "0" :border-radius "50%"}
      :onClick #(swap! collapsed? not)}
     [bs/glyphicon {:glyph (if @collapsed? "menu-down" "menu-up")}]]))

(defn timestamp-or-btn [collapsed? btn? timestamp]
  (fn [collapsed? btn? timestamp]
    (if btn?
      [expand-button collapsed?]
      [:div.row {:style {:margin "3px 0"}} [:span (timestamp-text timestamp)]])))

(defn repeated-lines [rest-timestamps {:keys [collapsed?]}]
  (fn [rest-timestamps {:keys [collapsed?]}]
    (let [max-lines (if @collapsed? 2 (count rest-timestamps))]
      [:div
       (doall
        (for [[idx timestamp] (map-indexed vector (concat (take max-lines rest-timestamps) ["btn"]))
              :let [btn? (= timestamp "btn")]]
          ^{:key idx} [timestamp-or-btn collapsed? btn? timestamp]))])))

(defn repeated-component [rest-timestamps]
  (let [collapsed? (reagent/atom true)]
    (fn [rest-timestamps]
      [:div.row {:style {:margin "10px 0 5px 0"}}
       [:div.container-fluid {:style {:border-left "0.2em solid #67b4d2"}}
        [repeated-lines rest-timestamps {:collapsed? collapsed?}]]])))

(defn event-item [{:keys [header-text source-user event-child timestamp repeated] :as args}]
  (fn [{:keys [header-text source-user event-child timestamp repeated] :as args}]
    (let [{:keys [last-timestamp rest-timestamps]} (sort-repeated timestamp repeated)]
      [bs/list-group-item
       (reagent/as-component
        [:div.container-fluid
         [:div.row
          [:div.col-sm-10
           [:div.container-fluid
            [:div.row [:h4 header-text [:span (timestamp-text last-timestamp)]]]
            [:div.row [:span event-child]]
            (when repeated [repeated-component rest-timestamps])]]
          [:div.col-sm-2.text-right [event-source source-user]]]])])))

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
  [{{query-str :query-str corpus :corpus} :data
    event-type :type timestamp :timestamp repeated :repeated}]
  (let [me (re-frame/subscribe [:me :username])]
    (fn [{{query-str :query-str corpus :corpus} :data
          event-type :type timestamp :timestamp repeated :repeated}]
      [event-item {:header-text event-type
                   :source-user @me
                   :event-child [:div.container-fluid
                                 [:div.row [:span "Corpus: " [:code corpus]]]
                                 [:div.row [:span "Query string: " [:code query-str]]]]
                   :repeated repeated
                   :timestamp timestamp}])))

(defmethod event-component :snippet
  [{{hit-id :hit-id corpus :corpus} :data
    event-type :type timestamp :timestamp repeated :repeated}]
  (let [me (re-frame/subscribe [:me :username])]
    (fn [{{hit-id :hit-id corpus :corpus} :data
          event-type :type timestamp :timestamp repeated :repeated}]
      [event-item {:header-text event-type
                   :source-user @me
                   :event-child [:div.container-fluid
                                 [:div.row [:span "Corpus: " [:code corpus]]]
                                 [:div.row [:span "Hit-id: " [:code hit-id]]]]
                   :timestamp timestamp}])))

(defmethod event-component :user-left-project
  [{{username :username} :data event-type :type timestamp :timestamp}]
  (fn [{{username :username} :data event-type :type timestamp :timestamp}]
    [event-item {:header-text event-type
                 :source-user username
                 :event-child [:span [:strong username] " left project"]
                 :timestamp timestamp}]))

(defmethod event-component :new-user-in-project
  [{{username :username} :data event-type :type timestamp :timestamp}]
  (fn [{{username :username} :data event-type :type timestamp :timestamp}]
    [event-item {:header-text event-type
                 :source-user username
                 :event-child [:span [:strong username] " has joined the project"]
                 :timestamp timestamp}]))

(defmethod event-component :new-user-role
  [{{new-role :new-role username :username} :data event-type :type timestamp :timestamp}]
  (fn [{{username :username} :data event-type :type timestamp :timestamp}]
    [event-item {:header-text event-type
                 :source-user username
                 :event-child [:span [:strong username] "'s role in project is now [" new-role "]"]
                 :timestamp timestamp}]))

(defmethod event-component :default
  [{data :data event-type :type timestamp :timestamp}]
  (fn [{data :data event-type :type timestamp :timestamp}]
    [event-item {:header-text event-type
                 :event-child (str data)
                 :timestamp timestamp}]))
