(ns cosycat.views.layout
  (:require [hiccup.core :refer [html]]
            [cosycat.views.imgs :refer [random-img]]))

(defn style [& info]
  {:style (.trim (apply str (map #(let [[kwd val] %]
                                    (str (name kwd) ":" val "; "))
                                 (apply hash-map info))))})

(declare base nav tabs footer)

(def bootstrap-css
  "vendor/css/bootstrap.min.css"
  ;; "http://cdnjs.cloudflare.com/ajax/libs/twitter-bootstrap/3.3.5/css/bootstrap.min.css"
  )

(def bootstrap-js
  "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/js/bootstrap.min.js")

(def jquery
  "http://code.jquery.com/jquery-1.9.1.min.js")

(defn base [{:keys [left right logged?]}]
  (html
   [:html
  {:lang "en"}
  [:head
   [:meta {:charset "utf-8"}]
   [:script {:type "text/javascript" :src jquery}]
   [:script {:type "text/javascript" :src bootstrap-js}]
   [:link
    {:type "text/css" :rel "stylesheet"
     :href "http://fonts.googleapis.com/css?family=Roboto+Condensed:400,300"}]
   [:link
    {:type "text/css" :rel "stylesheet"
     :href "http://fonts.googleapis.com/css?family=Roboto:300,400,500,700,400italic"}]
   [:link {:href bootstrap-css :rel "stylesheet"}]]
    [:body
     (style :margin-bottom "200px" :margin-top "40px")
     (nav logged?)
     [:div.container (style :padding "80px 40px 0 40px")
      [:div.row
       [:div.col-md-7 left]
       [:div.col-md-5 right]]]
     (footer)]]))

(defn nav [& [logged?]]
  [:nav.navbar.navbar-default.navbar-fixed-top ;navbar
   [:div.container
    [:div.navbar-header (style :padding "0 0 0 25px")
     [:button.navbar-toggle
      {:aria-controls "navbar"
       :aria-expanded "true"
        :data-toggle "collapse"}
       [:span.sr-only "Toggle Navigation"]
       [:span.icon-bar]
       [:span.icon-bar]
       [:span.icon-bar]]
      [:a.navbar-brand {:href "/"} "Cosycat"]]
    [:div.navbar-collapse.collapse
     [:ul.nav.navbar-nav.navbar-right (style :margin-right "25px")
      (if logged? [:li [:a {:href "/logout"} "Logout"]] [:li " "])
      [:li [:a {:href "/about"} "About"]]
      [:li [:a {:href "/cosycat"} "Query"]]]]]])

(defn footer []
  [:footer.nav.navbar.navbar-inverse.navbar-fixed-bottom ;footer
   (style :background-color "#2a2730" :color "#99979c")
   [:div.rc-v-box.display-flex
    (style
     :align-items "baseline"
     :-webkit-flex-flow "column nowrap"
     :-webkit-flex "0 0 80px"
     :-webkit-justify-content "flex-start"
     :-webkit-align-items "baseline"
     :flex "0 0 80px"
     :justify-content "flex-start"
     :flex-flow "column nowrap"
     :margin "0 15px 0 15px")
    [:br]
    [:div.rc-v-box.display-flex
     (style
      :align-items "stretch"
      :-webkit-flex-flow "column nowrap"
      :-webkit-flex "none"
      :-webkit-justify-content "flex-start"
      :flex "none"
      :justify-content "flex-start"
      :-webkit-align-items "stretch"
      :flex-flow "column nowrap"
      :margin "0 0 0 25px")
     [:div.rc-h-box.display-flex
      (style
       :-webkit-flex-flow "row nowrap"
       :flex-flow "row nowrap"
       :-webkit-flex "none"
       :flex "none"
       :-webkit-justify-content "flex-start"
       :justify-content "flex-start"
       :-webkit-align-items "stretch"
       :align-items "stretch")
      [:li
       [:a
        (assoc (style :color "white" :font-size "13px")
               :href "http://www.github.com/emanjavacas/cosycat")
        "GitHub"]]
      [:div.rc-gap (style :-webkit-flex "0 0 25px" :flex "0 0 25px" :width "25px") " "]
      [:li
       [:a
        (assoc (style :color "white" :font-size "13px")
               :href "https://www.uantwerpen.be/en/projects/mind-bending-grammars/")
        "MindBendingGrammars"]]
      [:div.rc-gap (style :-webkit-flex "0 0 25px" :flex "0 0 25px" :width "25px") " "]
      [:li [:a (assoc (style :color "white" :font-size "13px") :href "http://erc.europa.eu") "ERC"]]]
     [:div.rc-gap (style :-webkit-flex "0 0 5px" :flex "0 0 5px" :height "5px") " "]
     [:span (style :flex "none" :width "450px" :min-width "450px")
      [:p "Powered by ClojureScript and Reagent"]]]
     [:br]]])
