(ns cleebo.views.cleebo
  (:require [hiccup.page :refer [html5 include-js include-css]]))

(defn cleebo-page [& {:keys [csrf]}]
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
     (include-css "vendor/css/jquery-ui.min.css")]
    [:body
     [:div#app]
     (include-js "vendor/js/jquery/jquery-1.11.2.min.js")
     (include-js "vendor/js/jquery/jquery-ui.min.js")
     [:script (str "var csrf =\"" csrf "\";")]
;     [:script "cleebo.core.init();"]
     (include-js "js/compiled/app.js")]]))
