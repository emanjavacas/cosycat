(ns cosycat.tree
  (:require [reagent.core :as reagent]))

(defn style [depth] {:margin-left (str (+ 10 depth) "px") :cursor "pointer"})

(defn i [k v depth children & {:keys [tag] :or {tag :div}}]
  (let [open (reagent/atom true)]
    (fn [k v depth children & {:keys [tag] :or {tag :div}}]
      [tag
       {:style (merge (style depth))}
       [:span
        [:span {:on-click #(do (.stopPropagation %)  (swap! open not))}
         [:i.glyphicon
          {:class (if @open "glyphicon-triangle-bottom" "glyphicon-triangle-right")}]]
        (str k)]
       [:div {:style {:margin-left "15px"}} (when @open children)]])))

(defn recursive* [data depth]
  (cond (map? data)                     ;map
        (into [:div {:style (style depth)}]
              (mapv (fn [[k v]] [i k v depth (recursive* v (inc depth)) :tag :div])
                    data))
        (sequential? data)              ;sequential
        (into [:ul {:style (style depth) :list-style-type "none"}]
              (mapv (fn [[k v]] [i k v depth (recursive* v (inc depth)) :tag :li])
                    (map-indexed vector data)))
        :else (str data)))              ;else

(defn recursive [data] [recursive* data 0])

(defn data-tree [data]
  (fn [data]
    [recursive data]))
