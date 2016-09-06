(ns cosycat.views.error
  (:require [hiccup.core :refer [html]]
            [cosycat.views.layout :refer [bootstrap-css]]))

(defn error-page
  [{:keys [status title message] :or {status "404" title "Error!"}}]
  (html
   [:html
    [:head
     [:title "Something bad happened"]
     [:meta
      {:content "text/html; charset=UTF-8", :http-equiv "Content-Type"}]
     [:meta
      {:content "width=device-width, initial-scale=1.0",
       :name "viewport"}]
     [:link {:href bootstrap-css :rel "stylesheet"}]]
    [:body
     [:div.container-fluid
      [:div.row-fluid
       [:div.col-lg-12
        [:div.centering.text-center
         [:div.text-center
          [:h1 [:span.text-danger (str "Error: " status)]]
          [:hr]
          [:h2.without-margin title]
          [:h4.text-danger message]]]]]]]]))
