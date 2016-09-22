(ns cosycat.views.landing
  (:require [hiccup.core :refer [html]]
            [cosycat.views.imgs :refer [random-img]]
            [cosycat.views.layout :refer [base]]))

(defn landing-page [& {:keys [logged?] :or {:logged? false}}]
  (let [{href :href name :name} (random-img)]
    (base 
     {:left  [:div
              [:h2 "Welcome to the home page. "
               [:span.text-muted "Corpus Linguistics with ECCO & EEBO"]]
              [:p.lead "Donec ullamcorper nulla non metus auctor fringilla. 
                      Vestibulum id ligula porta felis euismod semper.
                      Praesent commodo cursus magna, vel scelerisque nisl.
                      Fusce dapibus, tellus ac cursus commodo."]]
      :right [:div.panel.panel-default
              [:div.panel-body {:style "text-align: center;"}
               [:img {:src (str "img/" href) :alt name
                      :style "max-heigth: 100%; max-width: 100%;"}]]
              [:div.panel-footer {:style "text-align: center;"} name]]
      :logged? logged?})))
