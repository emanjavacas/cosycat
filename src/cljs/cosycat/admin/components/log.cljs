(ns cosycat.admin.components.log
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [ajax.core :refer [GET]]
            [react-bootstrap.components :as bs]
            [cosycat.utils :refer [format]]
            [taoensso.timbre :as timbre]))

(defn get-more-log-handler [data-atom current]
  (fn [{:keys [lines]}]
    (swap! current + (count lines))
    (swap! data-atom into lines)))

(defn get-more-log-error-handler [data]
  (timbre/error data)
  (re-frame/dispatch [:notify {:message "Couldn't retrieve more"}]))

(defn get-more-log [data-atom current & {:keys [plus] :or {plus 10}}]
  (GET "/admin/log"
       {:params {:from @current :to (+ @current plus)}
        :handler (get-more-log-handler data-atom current)
        :error-handler get-more-log-error-handler}))

(defn retrieve-more-btn [data-atom current]
  [bs/button {:onClick #(get-more-log data-atom current)}
   [bs/glyphicon {:glyph "plus"}]])

(defn showing-label [from to]
  [:h4 (format "Showing log lines in range [%d-%d]" from to)])

(defn log-line [line]
  [:span.log-line
   {:style {:display "block"
            :cursor "pointer"
            :padding-left "2em"
            :word-wrap "break-word"            
            :text-indent "-2em"}}
   line [:br]])

(defn log-frame []
  (let [data-atom (reagent/atom []), current (reagent/atom 0)]
    (reagent/create-class
     {:component-will-mount #(get-more-log data-atom current)
      :reagent-render
      (fn []
        [:div.container-fluid     
         [:div.row
          [:div.col-lg-12
           [:div.panel.panel-default
            [:div.panel-heading             
             [:div.row
              [:div.col-lg-10 [showing-label (- @current (count @data-atom)) (+ @current @data-atom)]]
              [:div.col-lg-2 [:div.pull-right [retrieve-more-btn data-atom current]]]]]
            [:div.panel-body
             [:div.well
              {:style {:background-color "#3f3f3f"
                       :font-family "monospace"
                       :color "#dcdccc"}}
              (doall (for [{:keys [idx line]} @data-atom]
                       ^{:key idx} [log-line line]))]]]]]])})))
