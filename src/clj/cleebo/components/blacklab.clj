(ns cleebo.components.blacklab
  (:require [taoensso.timbre :as timbre]
            [cleebo.utils :refer [dummy-hit]]
            [cleebo.middleware :refer [wrap-safe]]
            [com.stuartsierra.component :as component]
            [cleebo.blacklab.core :as bl]))

(set! *warn-on-reflection* true)

(defn get-hits [bl query-id]
  (if-let [current-hits (:current-hits bl)]
    (get @current-hits query-id)))

(defn update-hits!
  "hits are stored independently per user - therefore no resource sharing"
  [bl query-id new-hits]
  (if-let [current-hits (:current-hits bl)]
    (swap! current-hits assoc query-id new-hits)))

(defn remove-hits!
  "hits are stored independently per user - therefore no resource sharing"
  [bl query-id]
  (if-let [current-hits (:current-hits bl)]
    (swap! current-hits dissoc query-id)))

(defn close-searchers!
  "a function to shut-down all searchers server-side"
  [bl]
  (doseq [[searcher-id searcher] (:searchers bl)]
    (timbre/info "Closing searcher: " searcher-id)
    (bl/close-searcher searcher)))

(defn ensure-searcher! [bl corpus]
  (let [searcher (get-in bl [:searchers corpus])]
    (bl/init-searcher! searcher)
    (await searcher)
    searcher))

(defrecord BLComponent [paths-map current-hits]
  component/Lifecycle
  (start [component]
    (timbre/info "Starting BLComponent with corpora" paths-map)
    (assoc component
           :searchers (zipmap (keys paths-map) (map bl/new-searcher (vals paths-map)))
           :current-hits (atom {})))
  (stop [component]
    (timbre/info "Shutting down BLComponent")
    (close-searchers! component)
    (if-let [current-hits (:current-hits component)]
      (doseq [query-id (keys @current-hits)]
        (remove-hits! component query-id)))))

(defn new-bl [paths-map]
  (map->BLComponent
   {:paths-map paths-map}))

(defmacro with-bl [bindings & body]
  `(let ~bindings
     (try
       (do ~@body)
       (catch Exception e#
         (throw (ex-info (:message (bean e#)) {})))
       (finally (close-searchers! ~(bindings 0))))))

(defn format-hit
  "pads hi-kwic with an empty hit in case of missing context"
  ([hit context]
   (format-hit hit context dummy-hit))
  ([hit context empty-hit-fn]
   (let [match-idxs (keep-indexed (fn [i hit] (when (:match hit) i)) hit)
         left  (- context (first match-idxs))
         right (- context (- (count hit) (inc (last match-idxs))))]
     (concat (map empty-hit-fn (range left)) hit (map empty-hit-fn (range right))))))

(defn format-hits [hits context]
  (map (fn [hit-map] (update hit-map :hit #(format-hit % context))) hits))

(defn- bl-query*
  "runs a query, updating the current-hits in the Blacklab component and
  returning a range of hit-kwics specified by `from`, `to` and `context`"
  ([bl corpus query-str from to context]
   (bl-query* bl corpus query-str from to context "default"))
  ([bl corpus query-str from to context query-id]
   (timbre/debug query-str)
   (let [s (ensure-searcher! bl corpus)
         hits-win (bl/query s query-str from to context update-hits! bl query-id)]
     {:results (format-hits hits-win context)
      :from from
      :to to
      :query-str query-str
      :query-size (bl/query-size (get-hits bl query-id))})))

(defn- bl-query-range*
  "returns a range of hit-kwics (:results) specified by `from`, `to` and `context`"
  ([bl corpus from to context]
   (bl-query-range* bl corpus from to context "default"))
  ([bl corpus from to context query-id]
   (let [searcher (ensure-searcher! bl corpus)
         hits (get-hits bl query-id)
         hits-range (bl/query-range searcher hits from to context)]
     {:results (format-hits hits-range context)
      :from from
      :to to})))

(defn- bl-sort-query*
  "sorts the entire hits of a previous query using `criterion` and `attribute` 
  returning a range of hit-kwics (:result) specified by `from`, `to` and `context`"
  ([bl corpus from to context sort-map]
   (bl-sort-query* bl corpus from to context sort-map "default"))
  ([bl corpus from to context {:keys [criterion attribute]} query-id]
   (let [searcher (ensure-searcher! bl corpus)
         hits (get-hits bl query-id)
         hits-range (bl/sort-query searcher hits from to context criterion attribute)]
     {:results (format-hits hits-range context)
      :from from
      :to to})))

(defn- bl-sort-range*
  "sorts the a given range of hits from a previous query using `criterion`
  and `attribute` returning a range of hit-kwics (:result) 
  specified by `from`, `to` and `context`"
  ([bl corpus from to context sort-map]
   (bl-sort-range* bl corpus from to context sort-map "default"))
  ([bl corpus from to context {:keys [criterion attribute]} query-id]
   (let [searcher (ensure-searcher! bl corpus)
         hits (get-hits bl query-id)
         hits-range (bl/sort-range searcher hits from to context criterion attribute)]
     {:results (format-hits hits-range context)
      :from from
      :to to})))

(defn- bl-snippet*
  "returns a snippet of raw text with n-words specified by `snippet size`
  around the match for a given hit specified by `hit-idx`"
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

;; (defonce my-atom (atom nil))
;; (add-watch
;;  my-atom :reset
;;  (fn [k r os ns]
;;    (println (str "Old state: " os " New state: " ns))))

;; (defn reset-timeout [time]
;;   (Thread/sleep time)
;;   (reset! my-atom (str "timeout: " time)))

;; (defn swap-timeout [time]
;;   (swap! my-atom
;;          (fn [a]
;;            (println (str "Started swap-timeout: " time))
;;            (Thread/sleep time)
;;            (str "timeout: " time))))

;; (future (reset-timeout 10000))
;; (reset-timeout 2000)

;; (future (swap-timeout 2000))
;; (swap-timeout 10000)

;; (import '(java.util.concurrent Executors))
;; (def *pool* (Executors/newFixedThreadPool
;;              (+ 2 (.availableProcessors (Runtime/getRuntime)))))

;; (defn dothreads! [f & {thread-count :threads
;;                        exec-count :times
;;                        :or {thread-count 4 exec-count 1}}]
;;   (dotimes [t thread-count]
;;     (.submit *pool* #(dotimes [_ exec-count] (f)))))

;; (def log-agent (agent 0))
;; (defn log [msg-id message]
;;   (println msg-id message)
;;   (inc msg-id))

;; (defn do-step [channel message]
;;   (Thread/sleep 1)
;;   (send-off log-agent log (str channel message)))

;; (defn steps [channel & messages]
;;   (doseq [msg messages]
;;     (do-step channel (str " " msg))))

;; (defn do-things []
;;   (let [messages ["ready to begin (step 0)"
;;                   "warming up (step 1)"
;;                   "going to sleep (step 2)"
;;                   "done! (step 3)"]]
;;     (dothreads! #(apply steps "alpha" messages))
;;     (dothreads! #(apply steps "beta" messages))
;;     (dothreads! #(apply steps "gamma" messages))))

;; (do-things)

;; (def path-maps (:blacklab-paths-map environ.core/env))
;; (def path-maps {"brown" "/home/enrique/code/cleebo/dev-resources/brown-tei-index/"})
;; (def corpus (first (:corpora environ.core/env)))
;; (def bl-component (-> (new-bl path-maps) (.start)))
;; (first (:results (bl-query bl-component "brown" "[pos='NP.*']*" 0 10 5)))

