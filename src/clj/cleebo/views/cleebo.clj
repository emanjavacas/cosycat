(ns cleebo.views.cleebo
  (:require [hiccup.core :refer [html]]))

(defn cleebo-page [& {:keys [csrf username]}]
  (html
   [:html
    {:lang "en"}
    [:head
     [:meta {:charset "utf-8"}]
     [:link {:href "vendor/css/bootstrap.min.css" :rel "stylesheet"}]
     [:link {:href "vendor/css/material-design-iconic-font.min.css" :rel "stylesheet"}]
     [:link {:href "vendor/css/hint.min.css" :rel "stylesheet"}]
     ;; [:link {:href "css/dashboard.css" :rel "stylesheet"}]     
     [:link {:href "css/main.css" :rel "stylesheet"}]
     [:link
      {:type "text/css" :rel "stylesheet"
       :href "http://fonts.googleapis.com/css?family=Roboto:300,400,500,700,400italic"}]
     [:link
      {:type "text/css" :rel "stylesheet"
       :href "http://fonts.googleapis.com/css?family=Roboto+Condensed:400,300"}]]
    [:body
     [:div#app]
     [:script (str "var csrf =\"" csrf "\";")]
     [:script (str "var username =\"" username "\";")]
     ;; [:script {:src "vendor/js/material-ui/add-robo.js"}]
     ;; [:script {:src "vendor/js/material-ui/material.js"}]
     [:script {:src "js/compiled/app.js"}]
     [:script "cleebo.core.init();"]]]))
