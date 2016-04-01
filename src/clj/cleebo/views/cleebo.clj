(ns cleebo.views.cleebo
  (:require [hiccup.page :refer [html5 include-js include-css]]))

(defn cleebo-page [& {:keys [csrf username]}]
  (html5
   [:html
    {:lang "en"}
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     (include-css "vendor/css/bootstrap.lumen.min.css")
     (include-css "vendor/css/material-design-iconic-font.min.css")
     (include-css "vendor/css/hint.min.css")
     (include-css "css/main.css")
     (include-css "css/loader.css")
     (include-css "css/table.css")
     (include-css "css/notifications.css")
     (include-css "http://fonts.googleapis.com/css?family=Roboto:300,400,500,700,400italic")
     (include-css "http://fonts.googleapis.com/css?family=Roboto+Condensed:400,300")
     (include-css "http://code.jquery.com/ui/1.11.2/themes/smoothness/jquery-ui.min.css")]
    [:body
     [:div#app]
     (include-js "js/compiled/app.js")
     (include-js "https://code.jquery.com/jquery-1.11.2.min.js")
     (include-js "http://code.jquery.com/ui/1.11.2/jquery-ui.min.js")
     [:script (str "var csrf =\"" csrf "\";")]
     [:script (str "var username =\"" username "\";")]
     [:script "cleebo.core.init();"]]]))
