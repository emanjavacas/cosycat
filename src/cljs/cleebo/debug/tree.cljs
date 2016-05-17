(ns cleebo.debug.tree
  (:require [reagent.core :as r]))

(declare data-frisk)

(def styles
  {:shell {:backgroundColor "#FAFAFA"}
   :strings {:color "#4Ebb4E"}
   :keywords {:color "purple"}
   :numbers {:color "blue"}
   :shell-visible-button {:backgroundColor "#4EE24E"}})

(defn root
  [{data :data expanded :expanded :or {:expanded false}}]
  (let [data (clojure.walk/walk #(with-meta % {:expanded (r/atom false)}) identity data)]
    (for [[k v] data]
      )))

(defn expand-button [{data :data path :path}]
  (let [expanded? (meta (get-in data path))]
    (fn []
      [:button {:style {:border "0"
                        :backgroundColor "transparent" :width "20px " :height "20px"}
                :on-click #()}
       [:svg {:viewBox "0 0 100 100"}
        [:polygon
         {:stroke "black"
          :points (if expanded? "0,0 100,0 50,100" "0,0 0,100 100,50")}]]])))

(defn collapse-all-button [emit-fn]
  [:button {:onClick #(emit-fn :collapse-all)
            :style {:padding "7px"
                    :border 1
                    :backgroundColor "lightgray"}}
   "Collapse all"])

(defn node [{:keys [data path]}]
  [:div
   (cond
     (string? data)  [:span {:style (:strings styles)} (str "\"" data "\"")]
     (keyword? data) [:span {:style (:keywords styles)} (str data)]
     (object? data)  (str data " " (.stringify js/JSON data))
     (number? data)  [:span {:style (:numbers styles)} data]
     :else           (str data))])

(defn key-val-node
  [{[k v] :data path :path expanded-paths :expanded-paths emit-fn :emit-fn}]
  [:div {:style {:display "flex"}}
   [:div {:style {:flex "0 0 auto" :padding "2px"}}
    [:span {:style (:keywords styles)} (str k)]]
   [:div {:style {:flex "1" :padding "2px"}}
    [data-frisk {:data v
                 :path (conj path k)
                 :expanded-paths expanded-paths
                 :emit-fn emit-fn}]]])

(defn list-vec-node [{:keys [data path expanded-paths emit-fn]}]
  (let [expanded? (get expanded-paths path)]
    [:div {:style {:display "flex"}}
     [:div {:style {:flex 0}} [expand-button {:expanded? expanded?
                                              :path path
                                              :emit-fn emit-fn}]]
     [:div {:style {:flex 1}} [:span (if (vector? data) "[" "(")]
      (if expanded?
        (map-indexed (fn [i x] ^{:key i} [data-frisk {:data x
                                                     :path (conj path i)
                                                     :expanded-paths expanded-paths
                                                     :emit-fn emit-fn}]) data)
        (str (count data) " items"))
      [:span (if (vector? data) "]" ")")]]]))

(defn set-node [{:keys [data path expanded-paths emit-fn]}]
  (let [expanded? (get expanded-paths path)]
    [:div {:style {:display "flex"}}
     [:div {:style {:flex 0}} [expand-button {:expanded? expanded?
                                              :path path
                                              :emit-fn emit-fn}]]
     [:div {:style {:flex 1}} [:span "#{"]
      (if expanded?
        (map-indexed (fn [i x] ^{:key i} [data-frisk {:data x
                                                      :path (conj path x)
                                                      :expanded-paths expanded-paths
                                                      :emit-fn emit-fn}]) data)
        (str (count data) " items"))
      [:span "}"]]]))

(defn map-node [{:keys [data path expanded-paths emit-fn]}]
  (let [expanded? (get expanded-paths path)]
    [:div {:style {:display "flex"}}
     [:div {:style {:flex 0}}
      [expand-button {:expanded? expanded? :path path :emit-fn emit-fn}]]
     [:div {:style {:flex 1}}
      [:span "{"]
      (if expanded?
        (map-indexed
         (fn [i x] ^{:key i}
           [key-val-node
            {:data x
             :path path
             :expanded-paths expanded-paths
             :emit-fn emit-fn}])
         data)
        [:span {:style (:keywords styles)} (clojure.string/join " " (keys data))])
      [:span "}"]]]))

(defn data-frisk [{:keys [data] :as all}]
  (cond (map? data) [map-node all]
        (set? data) [set-node all]
        (or (seq? data) (vector? data)) [list-vec-node all]
        (satisfies? IDeref data) [data-frisk (assoc all :data @data)]
        :else [node all]))

(defn conj-to-set [coll x]
  (conj (or coll #{}) x))

(defn emit-fn-factory [data-atom id]
  (fn [event & args]
    (case event
      :expand (swap! data-atom update-in [:data-frisk id :expanded-paths] conj-to-set (first args))
      :contract (swap! data-atom update-in [:data-frisk id :expanded-paths] disj (first args))
      :collapse-all (swap! data-atom assoc-in [:data-frisk id :expanded-paths] #{}))))

(defn root [data id state-atom]
  (let [data-frisk (:data-frisk @state-atom)
        emit-fn (emit-fn-factory state-atom id)]
    [:div
     [collapse-all-button emit-fn]
     [data-frisk {:data data
                  :path []
                  :expanded-paths (get-in data-frisk [id :expanded-paths])
                  :emit-fn emit-fn}]]))

(defn data-frisk-shell-visible-button
  [visible? toggle-visible-fn]
  [:div {:onClick toggle-visible-fn
         :style (merge {:padding "12px"
                        :position "fixed"
                        :right 0
                        :width "80px"
                        :text-align "center"}
                  (:shell-visible-button styles)
                  (when-not visible? {:bottom 0}))}
   (if visible? "Hide" "Data frisk")])

(defn data-frisk-shell [& data]
  (let [expand-by-default (reduce #(assoc-in %1 [:data-frisk %2 :expanded-paths] #{[]}) {} (range (count data)))
        state-atom (r/atom expand-by-default)]
    (fn []
      (let [data-frisk (:data-frisk @state-atom)
            visible? (:visible? data-frisk)]
        [:div {:style (merge {:position "fixed"
                              :right 0
                              :bottom 0
                              :width "100%"
                              :height "50%"
                              :max-height (if visible? "50%" 0)
                              :transition "all 0.3s ease-out"
                              :padding 0}
                        (:shell styles))}
         [data-frisk-shell-visible-button visible? (fn [_] (swap! state-atom assoc-in [:data-frisk :visible?] (not visible?)))]
         [:div {:style {:padding "10px"
                        :height "100%"
                        :box-sizing "border-box"
                        :overflow-y "scroll"}}
          (map-indexed (fn [id x] ^{:key id} [root x id state-atom]) data)]]))))
