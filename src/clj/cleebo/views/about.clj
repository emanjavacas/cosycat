(ns cleebo.views.about
  (:require [hiccup.core :refer [html]]
            [cleebo.views.layout :refer [base]]
            [cleebo.views.imgs :refer [random-img]]))

(defn about-page [& {:keys [logged?] :or {:logged? false}}]
  (let [{href :href name :name} (random-img)]
    (base 
     {:left  [:div
              [:h2 "About page. "]
              [:h3 [:span.text-muted "This page was created so&so."]]
              [:p.lead "Some text block"]
              [:p.lead "Followed"]
              [:p.lead "By another"]]
      :right [:div.panel.panel-default
              [:div.panel-body {:style "text-align: center;"}
               [:img.circle {:src (str "img/" href) :alt name}]]
              [:div.panel-footer {:style "text-align: center;"} name]]
      :logged? logged?})))
