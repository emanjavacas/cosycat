(ns cleebo.pages.home
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [cljs.core.async :refer [chan put! <! >! timeout close!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def imgs
  [["1_01_baxter.jpg" "Baxter"]
   ["1_02_owen.jpg" "Owen"]
   ["1_03_more.jpg" "More"]
   ["1_04_milton.jpg" "Milton"]
   ["1_05_prynne.jpg" "Prynne"]
   ["1_06_taylor.jpg" "Taylor"]
   ["1_07_lestrange.jpg" "L'estrange"]
   ["1_08_fox.jpg" "Fox"]
   ["2_01_burnet.jpg" "Burnet"]
;   ["2_03_stillingfleet.jpg" "Stillingfleet"]
   ["2_04_boyle.jpg" "Boyle"]
   ["2_06_whitby.jpg" "Whitby"]
   ["2_07_dryden.jpg" "Dryden"]
   ["2_09_penn.jpg" "Penn"]
   ["2_11_mather.jpg" "Mather"]
   ["2_13_tillotson.jpg" "Tillotson"]
   ["2_14_behn.jpg" "Behn"]
   ["2_15_bunyan.jpg" "Bunyan"]  
   ["2_16_willard.jpg" "Willard"]
   ["2_17_keach.jpg" "Keach"]
   ["2_18_salmon.jpg" "Salmon"]
   ["2_20_flavel.jpg" "Flavel"]
   ["2_22_poole.jpg" "Poole"]
   ["3_01_defoe.jpg" "Defoe"]
   ["3_02_swift.jpg" "Swift"]
   ["3_03_mather.jpg" "Mather"]
;   ["3_05_whiston.jpg" "Whiston"]
;   ["3_06_ward.jpg" "Ward"]
   ["3_07_wake.jpg" "Wake"]
   ["3_08_durfey.jpg" "Durfey"]
   ["4_01_addison.jpg" "Addison"]
   ["4_02_steele.jpg" "Steele"]
   ["4_03_clarke.jpg" "Clarke"]
   ["4_04_cibber.jpg" "Cibber"]
   ["4_06_haywood.jpg" "Haywood"]
   ["4_09_hoadly.jpg" "Hoadly"]
   ["x2_02_locke.jpg" "Locke"]
   ["x2_05_newton.jpg" "Newton"]])

(defn rand-int-chan [max-num cond-fn]
  (let [ch (chan)]
    (go
      (while (cond-fn)
        (<! (timeout (* 1000 5)))
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
        {:style {:box-shadow "3px 3px 3px #888888"}
         :src (str "/img/" src)}]
       [:br] [:br]
       name])))

(defn header []
  [re-com/box :size "70px" :margin "0 0 20px 0" :child
   [re-com/title :label "A corpus query interface (plus annotations)" :level :level1]])

(defn body [n]
  (fn [n]
    [re-com/h-box :height "500px"
     :children
     [[re-com/v-box :size "70%"
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
      [re-com/v-box :margin "0 45px 0 45px"; :align :center
       :children 
       [[header]
        [re-com/line]
        [body @n]
        [re-com/line]]])))
