(ns cleebo.components.blacklab
  (:require [taoensso.timbre :as timbre]
            [cleebo.utils :refer [wrap-safe dummy-hit]]
            [com.stuartsierra.component :as component]
            [cleebo.blacklab.core :as bl]))

(set! *warn-on-reflection* true)

(defn get-hits [bl query-id]
  (if-let [current-hits (:current-hits bl)]
    (get @current-hits query-id)))

(defn update-hits! [bl query-id new-hits]
  (if-let [current-hits (:current-hits bl)]
    (swap! current-hits assoc query-id new-hits)))

(defn remove-hits! [bl query-id]
  (if-let [current-hits (:current-hits bl)]
    (swap! current-hits dissoc query-id)))

(defn ensure-searcher! [bl searcher-id]
  (let [searcher (get-in bl [:searchers searcher-id])
        path (get-in bl [:paths-map searcher-id])]
    (if @searcher
      (do
        (timbre/debug "Searcher: " @searcher " already loaded")
        @searcher)
      (do (timbre/info "Loading searcher: " searcher-id)
          (reset! searcher (bl/make-searcher path))))))

(defn close-searcher! [bl searcher-id]
  (let [searcher (get-in bl [:searchers searcher-id])]
    (when @searcher
      (do (bl/destroy-searcher @searcher)
          (reset! searcher nil)))))

(defn close-all-searchers! [bl]
  (doseq [[searcher-id searcher] (:searchers bl)]
    (timbre/info "Closing searcher: " searcher-id)
    (close-searcher! bl searcher-id)))

(defrecord BLComponent [paths-map current-hits hits-handler ws]
  component/Lifecycle
  (start [component]
    (timbre/info "Starting BLComponent with corpora" paths-map)
    (assoc component
           :searchers (zipmap (keys paths-map) (repeatedly (fn [] (atom nil))))
           :current-hits (atom {})))
  (stop [component]
    (timbre/info "Shutting down BLComponent")
    (close-all-searchers! component)
    (if-let [current-hits (:current-hits component)]
      (doseq [query-id (keys @current-hits)]
        (remove-hits! component query-id)))))

(defn new-bl
  ([paths-map]
   (new-bl paths-map bl/hits-handler))
  ([paths-map hits-handler]
   (map->BLComponent
    {:paths-map paths-map
     :hits-handler hits-handler})))

(defmacro with-bl [bindings & body]
  `(let ~bindings
     (try
       (do ~@body)
       (catch Exception e#
         (throw (ex-info (:message (bean e#)) {})))
       (finally (close-all-searchers! ~(bindings 0))))))

(defn format-hit
  "pads hi-kwic with an empty hit in case of missing context"
  ([hit context]
   (format-hit hit context dummy-hit))
  ([hit context empty-hit-fn]
   (let [match-idxs (keep-indexed (fn [i hit] (when (:match hit) i)) hit)
         left  (- context (first match-idxs))
         right (- context (- (count hit) (inc (last match-idxs))))]
     (concat (map empty-hit-fn (range left))
             hit
             (map empty-hit-fn (range right))))))

(defn format-hits [hits context]
  (map (fn [hit-map] (update hit-map :hit #(format-hit % context))) hits))

(defn- bl-query*
  "runs a query, updating the current-hits in the Blacklab component and
  returning a valid xhr response with a range of hit-kwics (:result) 
  specified by `from`, `to` and `context`"
  ([bl corpus query-str from to context]
   (bl-query* bl corpus query-str from to context "default"))
  ([bl corpus query-str from to context query-id]
   (let [s (ensure-searcher! bl corpus)
         h-h (:hits-handler bl)
         hits-range (bl/query s h-h query-str from to context update-hits! bl query-id)]
     {:results (format-hits hits-range context)
      :from from
      :to to
      :query-str query-str
      :query-size (bl/query-size (get-hits bl query-id))})))

(defn- bl-query-range*
  "returns a valid xhr response with a range of hit-kwics (:results)
    specified by `from`, `to` and `context`"
  ([bl corpus from to context]
   (bl-query-range* bl corpus from to context "default"))
  ([bl corpus from to context query-id]
   (let [searcher (ensure-searcher! bl corpus)
         hits-handler (:hits-handler bl)
         hits (get-hits bl query-id)
         hits-range (bl/query-range searcher hits hits-handler from to context)]
     {:results (format-hits hits-range context)
      :from from
      :to to})))

(defn- bl-sort-query*
  "sorts the entire hits of a previous query using `criterion` and `prop-name` 
  returning a valid xhr response with a range of hit-kwics (:result) 
  specified by `from`, `to` and `context`"
  ([bl corpus from to context sort-map]
   (bl-sort-query* bl corpus from to context sort-map "default"))
  ([bl corpus from to context {:keys [criterion prop-name]} query-id]
   (let [scher (ensure-searcher! bl corpus)
         h-h (:hits-handler bl)
         hits (get-hits bl query-id)
         hits-range (bl/sort-query scher hits h-h from to context criterion prop-name)]
     {:results (format-hits hits-range context)
      :from from
      :to to})))

(defn- bl-sort-range*
  "sorts the a given range of hits from a previous query using `criterion`
  and `prop-name` returning a valid xhr response with a range of hit-kwics (:result) 
  specified by `from`, `to` and `context`"
  ([bl corpus from to context sort-map]
   (bl-sort-range* bl corpus from to context sort-map "default"))
  ([bl corpus from to context {:keys [criterion prop-name]} query-id]
   (let [scher (ensure-searcher! bl corpus)
         h-h (:hits-handler bl)
         hits (get-hits bl query-id)
         hits-range (bl/sort-range scher hits h-h from to context criterion prop-name)]
     {:results (format-hits hits-range context)
      :from from
      :to to})))

(defn- bl-snippet*
  ([bl hit-idx snippet-size]
   (bl-snippet* bl hit-idx snippet-size "default"))
  ([bl hit-idx snippet-size query-id]
   (let [hits (get-hits bl query-id)
         snippet (bl/snippet hits hit-idx snippet-size)]
     {:snippet snippet :hit-idx hit-idx})))

(def bl-query (wrap-safe bl-query*))
(def bl-query-range (wrap-safe bl-query-range*))
(def bl-sort-query (wrap-safe bl-sort-query*))
(def bl-sort-range  (wrap-safe bl-sort-range*))
(def bl-snippet (wrap-safe bl-snippet*))

