(ns cleebo.query.components.sort-toolbar
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cleebo.components :refer [dropdown-select]]
            [cleebo.utils :refer [->default-map]]
            [cleebo.app-utils :refer [dekeyword]]
            [taoensso.timbre :as timbre]))

(defn on-click-sort                     ;redo this
  [route]
  (fn []
    (re-frame/dispatch [:query-sort route :results-frame])))

(defn multiple-dropdown-label [local-label]
  (fn [local-label]
    [bs/button {:style {:pointer-events "none !important"}}
     (let [total-labels (count local-label)]
       (doall (for [[idx [k v-atom]] (map-indexed vector local-label)]
                ^{:key idx} [:span (str @v-atom (if (= (inc idx) total-labels) "" ":"))])))]))

(defn multiple-select [local-label select-fn]
  (fn [_ [k sub-k]]
    (reset! (get local-label (keyword k)) sub-k)
    (select-fn (keyword k) sub-k)))

(defn sub-menu
  "displays a stretch of options inside the dropdown-menu"
  [k v idx total-options]
  (concat
   [^{:key (str k "header")} [bs/menu-item {:header true} (str k)]]
   (for [{:keys [key label]} v] ^{:key key} [bs/menu-item {:eventKey [k label]} label])
   (when-not (= (inc idx) total-options)
     [^{:key (str k "divide")} [bs/menu-item {:divider true}]])))

(defn multiple-dropdown
  ":param options:    {:attribute {key val} :position {key val} :facet {key val}}
   :param model:      {:attribute att-model :position pos-model :facet fac-model}
   :param select-fn:  gets called (select-fn (:attribute|:position|:facet) new-value)"
  [{:keys [model options select-fn header]}]
  {:pre [(= (into (hash-set) (keys model)) (into (hash-set) (keys options)))]}
  (let [local-label (zipmap (keys model) (map reagent/atom (vals model)))]
    (fn [{:keys [model options select-fn header] :as args}]
      [bs/dropdown
       (merge
        {:id "my-multidropdown" :onSelect (multiple-select local-label select-fn)}
        (dissoc args :model :options :select-fn :header))
       [multiple-dropdown-label local-label]
       [bs/dropdown-toggle]
       [bs/dropdown-menu
        (apply
         concat
         (let [total-options (count options)]
           (for [[idx [k v]] (map-indexed vector options)]
             (sub-menu k v idx total-options))))]])))

(defn sort-toolbar []
  (let [sort-opts (re-frame/subscribe [:settings :query :sort-opts])
        corpus (re-frame/subscribe [:settings :query :corpus])
        sort-props (re-frame/subscribe [:corpus-info :sort-props])]
    (fn []
      [:div.row
       [:div.col-lg-12.pull-left
        (let [sort-opts-total (count @sort-opts)]
          [bs/button-toolbar
           (doall
            (for [[idx opts] (map-indexed vector @sort-opts)]
              ^{:key (str "multi-" idx)}
              [multiple-dropdown
               {:model opts
                :options {:position (->default-map ["match" "left" "right"])
                          :attribute (->default-map (mapv dekeyword (keys @sort-props)))
                          :facet (->default-map ["sensitive" "insensitive"])} ;todo: depends on corpus
                :select-fn #(re-frame/dispatch [:set-settings [:query :sort-opts idx %] %2])}]))
           [bs/button
            {:onClick #(re-frame/dispatch [:add-default-opts-map :sort-opts])
             :bsStyle "primary"}
            (if (zero? sort-opts-total)
              "add sort criterion"
              [bs/glyphicon {:glyph "plus"}])]
           (when-not (zero? sort-opts-total)
             [bs/button
              {:onClick #(re-frame/dispatch [:remove-opts-map :sort-opts])
               :bsStyle "primary"}
              [bs/glyphicon {:glyph "minus"}]])
           (when-not (zero? sort-opts-total)
             [bs/button
              {:onClick (on-click-sort :sort-query)} ;todo
              "Sort"])])]])))
