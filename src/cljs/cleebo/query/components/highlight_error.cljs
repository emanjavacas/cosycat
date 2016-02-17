(ns cleebo.query.components.highlight-error
  (:require [goog.string :as gstr]))

(defn highlight-n [s n]
  (let [pre (subs s 0 n)
        post (subs s (inc n))
        target [:span
                {:style {:background-color "rgba(255, 0, 0, 0.3)"}}
                (nth s n)]]
    [:tt.text-center pre target post]))

(defn replace-char [s n replacement]
  (let [pre (subs s 0 n)
        post (subs s (inc n))]
    (str pre replacement post)))

(defn nbsp [& [n]]
  (gstr/unescapeEntities "&nbsp;"))

(defn highlight-error [{query-str :query-str at :at}]
  [:div
   {:style {:display "inline-block"}}
   [:div.alert.alert-danger
    {:style {:border-right "none"
             :color "#333"
             :background-color "rgba(255, 0, 0, 0.1)"
             :padding "0px"
             :border-left "4px solid rgba(255, 0, 0, 0.8)"
             :border-top "none"
             :border-radius "0px"
             :border-bottom "none"
             :margin "0px"}}
    (highlight-n query-str at)]
   [:tt.text-center
    {:style {:padding-left "3.5px"}}
    (replace-char
     (apply str (repeat (count query-str) (nbsp)))
     at
     (gstr/unescapeEntities "&#x21D1;"))]])

