(ns cleebo.pages.query
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [<! >! put! close! timeout chan]]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def url "ws://146.175.15.30:3449/ws")
(defn in-chan [url & {:keys [format] :or {:format :transit-json}}]
  (let [out (chan)]
    (go
      (loop [in (:ws-channel (<! (ws-ch url {:format format})))]
        (let [{:keys [message error] :as data} (<! in)]
          (if data
            (do (cond message (>! out message)
                      error   (close! in))
                (recur in))
            (do (timbre/debug "Connection failed!")
                (<! (timeout 1000))
                (recur (:ws-channel (<! (ws-ch url {:format format})))))))))
    out))

(defn out-chan [url & {:keys [format] :or {:format :transit-json}}]
  (let [in (chan)]
    (go
      (loop [out (:ws-channel (<! (ws-ch url {:format format})))]
        (let [data (<! in)]
          (>! out data)
          (recur out))))
    in))

(defn query-field []
  [:h2.page-header {:style {:font-weight "5em"}}
   [:div.row
    [:div.col-sm-3 "Query Panel"]
    [:div.col-sm-9
     [:div.form-horizontal
      [:div.input-group      
       [:input.form-control
        {:name "query"
         :type "text"
         :id "query"
         :placeholder "Example: [pos='.*\\.']"
         :autocorrect "off"
         :autocapitalize "off"
         :spellcheck "false"}]
       [:span.input-group-addon
        [re-com/md-icon-button :md-icon-name "zmdi-search" :size :smaller]]]]]]])

(defn results-frame []
  (let [in (in-chan url)
        out (out-chan url)
        data (atom nil)]
    (fn []
      [re-com/v-box :children
       [[re-com/h-box :align :center
         :children
         [[re-com/md-icon-button
           :md-icon-name "zmdi-edit"
           :on-click #(go (>! out (str "client put: " (rand-int 10))))]
          [re-com/md-icon-button
           :md-icon-name "zmdi-copy"
           :on-click #(go (let [msg (<! in)] (timbre/debug msg) (reset! data msg)))]]]
        [re-com/box :child [:div @data]]]])))

(defn annotation-frame []
  [:div "annotation frame"])

(defn query-main []
  (let [annotation? (atom true)]
    (fn []
      [re-com/v-box :gap "50px"
       :children 
       [[re-com/box :align :center :child [results-frame]]
        (when @annotation?
          [re-com/box :align :center :child [annotation-frame]])]])))

(defn query-panel []
  [:div
   [query-field]
   [query-main]])
