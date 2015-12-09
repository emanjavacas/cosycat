(ns cleebo.pages.home
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [cljs.core.async :refer [chan put! <! >! timeout close!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def imgs
  [["1_02_owen.jpg" "Owen"]
   ["1_04_milton.jpg" "Milton"]
   ["1_05_prynne.jpg" "Prynne"]
   ["1_08_fox.jpg" "Fox"]
   ["2_03_stillingfleet.jpg" "Stillingfleet"]
   ["2_04_boyle.jpg" "Boyle"]
   ["2_09_penn.jpg" "Penn"]
   ["2_13_tillotson.jpg" "Tillotson"]
   ["2_18_salmon.jpg" "Salmon"]
   ["3_01_defoe.jpg" "Defoe"]
   ["3_06_ward.jpg" "Ward"]
   ["x2_05_newton.jpg" "Newton"]])

(defn rand-int-chan [max-num cond-fn]
  (let [ch (chan)]
    (go
      (while (cond-fn)
        (<! (timeout (* 1000 15)))
        (>! ch (rand-int max-num)))
      (close! ch))
    ch))

(defn cond-fn []
  (let [active-panel (re-frame/subscribe [:active-panel])]
    (= @active-panel :home-panel)))

(defn img-renderer [n]
  (fn [n]
    (let [[src name] (nth imgs n)]
      [:div.text-center
       [:img
        {:style {:max-height "350px" :width "auto" :box-shadow "3px 3px 3px #888888"}
         :src (str "/img/" src)}]
       [:br] [:br]
       name])))

(defn header []
  [re-com/box :size "70px" :child
   [re-com/title :label "A corpus query interface (plus annotations)" :level :level2]])

(defn footerlink [label href]
  (fn []
    [:a {:href href :style {:color "white" :font-size "13px"}} label]))

(defn footer []
  [:footer.nav.navbar.navbar-inverse.navbar-fixed-bottom
   {:style {:background-color "#2a2730" :color "#99979c"}}
   [re-com/v-box :align :baseline :size "80px" :margin "0 15px 0 15px" 
    :children
    [[:br]
     [re-com/v-box :margin "0 0 0 20px" :gap "5px"
      :children 
      [[re-com/h-box :gap "25px"
        :children
        [[:li [footerlink "GitHub" "http://www.github.com/emanjavacas/cleebo"]]
         [:li [footerlink "MindBendingGrammars" "https://www.uantwerpen.be/en/projects/mind-bending-grammars/"]]
         [:li [footerlink "ERC" "http://erc.europa.eu"]]]]
       [re-com/p "Powered by ClojureScript and Reagent"]]]
     [:br]]]])

(defn body [n]
  (fn [n]
    [re-com/h-box :height "500px"    ;body
     :children
     [[re-com/v-box :size "80%"
       :children
       [[:br]
        [re-com/p "Welcome to the home page of Cleebo:  " "Corpus Linguistics with EEBO."]
        [re-com/p "Add some important stuff here that describes what the app can do."]]]
      [re-com/line]
      [re-com/box :size "30%" :child [img-renderer n]]]]))

(defn home-panel []
  (let [max-num (count imgs)
        n (reagent/atom (rand-int max-num))
        ch (rand-int-chan max-num cond-fn)]
    (go-loop []
      (when-let [new (<! ch)]
        (reset! n new)
        (recur)))
    (fn []
      [re-com/v-box
       :children 
       [[re-com/v-box :margin "0 45px 0 45px"; :align :center
         :children 
         [[header]
          [re-com/line]
          [body @n]]]
        [re-com/line]
        [footer]]])))
