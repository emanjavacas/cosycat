(ns cleebo.blacklab
  (:require [taoensso.timbre :as timbre]
            [cleebo.utils :refer [wrap-safe dummy-hit]]
            [com.stuartsierra.component :as component]
            [cleebo.blacklab.core :as bl]))

(defn get-hits [bl-component query-id]
  (if-let [current-hits (:current-hits bl-component)]
    (get @current-hits query-id)))

(defn update-hits! [bl-component query-id new-hits]
  (if-let [current-hits (:current-hits bl-component)]
    (swap! current-hits assoc query-id new-hits)))

(defn remove-hits! [bl-component query-id]
  (if-let [current-hits (:current-hits bl-component)]
    (swap! current-hits dissoc query-id)))

(defn ensure-searcher! [bl-component searcher-id]
  (let [searcher (get-in bl-component [:searchers searcher-id])
        path (get-in bl-component [:paths-map searcher-id])]
    (if @searcher
      @searcher
      (do (timbre/info "Loading searcher: " searcher-id)
          (reset! searcher (bl/make-searcher path))))))

(defn close-searcher! [bl-component searcher-id]
  (let [searcher (get-in bl-component [:searchers searcher-id])]
    (when @searcher
      (do (bl/destroy-searcher @searcher)
          (reset! searcher nil))))  )

(defn close-all-searchers! [bl-component]
  (doseq [[searcher-id searcher] (:searchers bl-component)]
    (timbre/info "Closing searcher: " searcher-id)
    (close-searcher! bl-component searcher-id)))

(defrecord BLComponent [paths-map current-hits hits-handler]
  component/Lifecycle
  (start [component]
    (timbre/info "Starting BLComponent")
    (assoc component
           :searchers (zipmap (keys paths-map) (repeat (atom nil)))
           :current-hits (atom {})))
  (stop [component]
    (timbre/info "Shutting down BLComponent")
    (doseq [query-id (keys @(:current-hits component))]
      (remove-hits! component query-id))))

(defn new-bl-component
  ([paths-map]
   (new-bl-component paths-map bl/hits-handler))
  ([paths-map hits-handler]
   (map->BLComponent
    {:paths-map paths-map
     :hits-handler hits-handler})))

(defmacro with-bl-component [bindings & body]
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

(defn numerize-hits
  "formats and adds numeric ids to the hit-kwics
  {0 {:hit [{:id id :word word} {:id id :word word}] :num 0 :meta meta}}"
  [hits from to context]
  {:pre (= (count hits) (- to from))}
  (let [formatted (map (fn [hit-map] (update hit-map :hit #(format-hit % context))) hits)]
    formatted))

(defn- bl-query*
  "runs a query, updating the current-hits in the Blacklab component and
  returning a valid xhr response with a range of hit-kwics (:result) 
  specified by `from`, `to` and `context`"
  ([bl-component corpus query-str from to context]
   (bl-query* bl-component corpus query-str from to context "default"))
  ([bl-component corpus query-str from to context query-id]
   (let [searcher (ensure-searcher! bl-component corpus)
         hits-handler (:hits-handler bl-component)
         searcher (ensure-searcher! bl-component corpus)
         hits-range (bl/query searcher hits-handler query-str from to context
                              update-hits! bl-component query-id)]
     {:results (numerize-hits hits-range from to context)
      :from from
      :to to
      :query-str query-str
      :query-size (bl/query-size (get-hits bl-component query-id))})))

(defn- bl-query-range*
  "returns a valid xhr response with a range of hit-kwics (:results)
    specified by `from`, `to` and `context`"
  ([bl-component corpus from to context]
   (bl-query-range* bl-component corpus from to context "default"))
  ([bl-component corpus from to context query-id]
   (let [searcher (ensure-searcher! bl-component corpus)
         hits-handler (:hits-handler bl-component)
         hits (get-hits bl-component query-id)
         hits-range (bl/query-range searcher hits hits-handler from to context)]
     {:results (numerize-hits hits-range from to context)
      :from from
      :to to})))

(defn- bl-sort-query*
  "sorts the entire hits of a previous query using `criterion` and `prop-name` 
  returning a valid xhr response with a range of hit-kwics (:result) 
  specified by `from`, `to` and `context`"
  ([bl-component corpus from to context sort-map]
   (bl-sort-query* bl-component corpus from to context sort-map "default"))
  ([bl-component corpus from to context {:keys [criterion prop-name]} query-id]
   (let [searcher (ensure-searcher! bl-component corpus)
         hits-handler (:hits-handler bl-component)
         hits (get-hits bl-component query-id)
         hits-range (bl/sort-query searcher hits hits-handler from to context
                                   criterion prop-name)]
     {:results (numerize-hits hits-range from to context)
      :from from
      :to to})))

(defn- bl-sort-range*
  "sorts the a given range of hits from a previous query using `criterion` and `prop-name` 
  returning a valid xhr response with a range of hit-kwics (:result) 
  specified by `from`, `to` and `context`"
  ([bl-component corpus from to context sort-map]
   (bl-sort-range* bl-component corpus from to context sort-map "default"))
  ([bl-component corpus from to context {:keys [criterion prop-name]} query-id]
   (let [searcher (ensure-searcher! bl-component corpus)
         hits-handler (:hits-handler bl-component)
         hits (get-hits bl-component query-id)
         hits-range (bl/sort-range searcher hits hits-handler from to context
                                   criterion prop-name)]
     {:results (numerize-hits hits-range from to context)
      :from from
      :to to})))

(def bl-query (wrap-safe bl-query*))

(def bl-query-range (wrap-safe bl-query-range*))

(def bl-sort-query (wrap-safe bl-sort-query*))

(def bl-sort-range  (wrap-safe bl-sort-range*))

