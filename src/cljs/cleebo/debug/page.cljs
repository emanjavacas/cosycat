(ns cleebo.debug.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer [nbsp]]
            [cleebo.schemas.app-state-schemas :refer [db-schema]]
            [cleebo.localstorage :as ls]
            [cleebo.debug.tree :as tree]))

(defn kv-pairs [s]
  (into [:div]
        (map
         (fn [[k v]]
           [:div.row
            {:style {:width "95%"}}
            [:div.col-sm-2 (str k)]
            [:div.col-sm-10 (str v)]])
         s)))

(defn summary-session []
  (let [query-opts (re-frame/subscribe [:query-opts])
        query-results (re-frame/subscribe [:query-results])
        results (re-frame/subscribe [:session :results-by-id])
        result-keys (re-frame/subscribe [:session :results])
        marked-hits (re-frame/subscribe [:marked-hits])
        users (re-frame/subscribe [:session :users])]
    (fn []
      [:div.container-fluid
       [:div.row [:h4 [:span.text-muted "Query Options"]]]
       [:div.row [kv-pairs @query-opts]]
       [:div.row [:h4 [:span.text-muted "Query Results"]]]
       [:div.row [kv-pairs @query-results]]
       [:div.row [:h4 [:span.text-muted "Users"]]]
       [:div.row
        (doall (for [user @users]
                 ^{:key (:username user)}
                 [kv-pairs user]))]
       [:div.row [:h4 [:span.text-muted "Results"]]]
       (into [:div] (map (fn [k] [:div.row k]) @result-keys))
       [:div.row [:h4 [:span.text-muted "Results by key"]]]
       [:div.row [kv-pairs (zipmap (keys @results) (vals @results))]]
       [:div.row [:h4 [:span.text-muted "Marked hits"]]]
       [:div.row [kv-pairs (map (juxt :id identity) @marked-hits)]]])))

(defn ls-dump []
  [bs/button
   {:on-click ls/dump-db}
   "Dump to LocalStorage"])

(defn ls-print []
  [bs/button
   {:on-click #(let [ks (ls/recover-all-db-keys)]
                 (.log js/console ks))}
   "Print LocalStorages to console"])

(defn ls-reload []
  [bs/button
   {:on-click #(if-let [ks (ls/recover-all-db-keys)]
                 (let [dump (ls/recover-db (last ks))]
                   (re-frame/dispatch [:load-db dump]))
                 (timbre/info "No DBs in LocalStorage"))}
   "Reload last db from LocalStorage"])

(defn notification-button []
  [bs/button
   {:on-click
    #(re-frame/dispatch
      [:notify {:message "Hello World! How are you doing?"}])}
   "Notify"])

(defn style [path] {:margin-left (str (* 10 (count path)) "px") :cursor "pointer"})

(defn i [k v path children & {:keys [tag] :or {tag :div}}]
  (let [background (reagent/atom "white")]
    (fn [k v path children & {:keys [tag] :or {tag :div}}]
      [tag
       {:style (merge (style path) {:background-color @background})
        :on-click (fn [e]
                    (.stopPropagation e)
                    (swap! background #(condp = % "white" "black" "black" "white")))}
       (str "P<" k ">")
       children])))

(defn recursive* [data path]
  (cond (map? data) (into [:div {:style (style path)}]
                          (mapv (fn [[k v]] [i k v path (recursive* v (conj path k))]) data))
        (sequential? data) (into [:ul {:style (style path)}]
                                 (mapv (fn [[k v]] [i k v path (recursive* v (conj path k)) :tag :li])
                                       (map-indexed vector data)))
        :else (str (nbsp (* 10 (count path))) "C<" data ">")))

(def d {:a [{:d "a"} {:b {:d [1 2 3] :e [{:a "a" :b "b"}] :f "A!"}}]
        :c {:e false}
        :d {:f "A"}})

(defn recursive [data]
  [recursive*
   (clojure.walk/walk #(with-meta % {:show? (reagent/atom false)}) identity data)
   []])

(defn frisk []
  (let [db (re-frame/subscribe [:db])
        d {:a [{:d "a"} {:b {:d [1 2 3] :e [{:a "a" :b "b"}] :f "A!"}}]
           :c {:e false}
           :d {:f "A"}}]
    (fn []
      [recursive @db])))

(defn debug-panel []
  (fn []
    [:div.container-fluid
     [:div.row
      [:h3 [:span.text-muted "Debug Panel"]]]
     [:div.row [:hr]]
     [:div.row
      [bs/button-toolbar
       [notification-button]
       [ls-dump]
       [ls-print]
       [ls-reload]]]
     [:div.row [frisk]]
     [:div.row [summary-session]]]))
