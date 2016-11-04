(ns cosycat.backend.handlers.query
  (:require [re-frame.core :as re-frame]
            [cosycat.backend.middleware :refer [standard-middleware no-debug-middleware]]
            [cosycat.backend.db :refer [default-project-session]]
            [cosycat.query-backends.core :refer [ensure-corpus]]
            [cosycat.query-backends.protocols :refer [query query-sort snippet query-hit]]
            [cosycat.utils :refer [filter-marked-hits current-results format]]
            [cosycat.app-utils :refer [parse-token-id parse-hit-id]]
            [taoensso.timbre :as timbre]))

(defn pager-next
  ([size page-size] (pager-next size page-size 0))
  ([size page-size from]
   (let [to (+ from page-size)]
     (cond
       (= from size) [0 (min page-size size)]
       (>= to size)  [from size]
       :else         [from to]))))

(defn pager-prev
  ([size page-size] (pager-prev size page-size 0))
  ([size page-size from]
   (let [new-from (- from page-size)]
     (cond (zero? from)     [(- size page-size) size]
           (zero? new-from) [0 page-size]
           (neg?  new-from) [0 (+ new-from page-size)]
           :else            [new-from from]))))

(defn merge-hit [old-hit new-hit]
  (if (get-in old-hit [:meta :marked])
    (assoc-in new-hit [:meta :marked] true)
    new-hit))

(defn merge-results
  "on new results, update current results preserving marked metadata"
  [old-results results]
  (let [new-results (zipmap (map :id results) results)] ;normalized results
    (reduce-kv (fn [m k v]
                 (if (get-in old-results [k :meta :marked])
                   (assoc m k (merge-hit (get old-results k) (get new-results k)))
                   m))
               new-results
               old-results)))

(defn page-margins [results]
  (let [margins (for [{hit :hit id :id} results ;seq; avoid empty seq
                      :let [{doc :doc start :id} (-> hit first :id parse-token-id)
                            end (-> hit last :id parse-token-id :id)]]
                  {:start start :end end :hit-id id :doc doc})]
    (vec margins)))

(re-frame/register-handler
 :set-query-results
 standard-middleware
 (fn [db [_ {:keys [results-summary results status]}]]
   (re-frame/dispatch [:stop-throbbing :results-frame])
   (re-frame/dispatch [:fetch-annotations {:page-margins (page-margins results)}])
   (let [active-project (get-in db [:session :active-project])
         path-fn (fn [key] [:projects active-project :session :query key])]
     (-> db
         (assoc-in [:projects active-project :session :status] status)
         (update-in (path-fn :results-summary) merge results-summary)
         (assoc-in (path-fn :results) (map :id results))
         (update-in (path-fn :results-by-id) merge-results results :has-marked? true)))))

(re-frame/register-handler
 :unset-query-results
 standard-middleware
 (fn [db _]
   (let [active-project (get-in db [:session :active-project])
         project (get-in db [:projects active-project])]
     (assoc-in db [:projects active-project :session] (default-project-session project)))))

(re-frame/register-handler
 :query-error
 standard-middleware
 (fn [db [_ {:keys [message code] :as args}]]
   (re-frame/dispatch [:stop-throbbing :results-frame])
   (let [active-project (get-in db [:session :active-project])]
     (-> db
         (assoc-in [:projects active-project :session :status :status] :error)
         (assoc-in [:projects active-project :session :status :content]
                   {:message message :code code})))))

(defn find-corpus-config [db corpus-name]
  (some #(when (= corpus-name (:corpus %)) %) (db :corpora)))

(defn run-query [corpus-config query-str query-opts sort-opts filter-opts]
  (if (and (empty? sort-opts) (empty? filter-opts))
    (query (ensure-corpus corpus-config) query-str query-opts)
    (query-sort (ensure-corpus corpus-config) query-str query-opts sort-opts filter-opts)))

(re-frame/register-handler
 :query
 (fn [db [_ query-str & {:keys [set-active] :or {set-active false}}]]
   (let [{:keys [corpus query-opts sort-opts filter-opts]} (get-in db [:settings :query])]
     (re-frame/dispatch [:start-throbbing :results-frame])
     (if set-active
       (re-frame/dispatch [:set-active-query set-active])
       (re-frame/dispatch [:unset-active-query]))
     (re-frame/dispatch
      [:register-user-project-event {:data {:query-str query-str :corpus corpus} :type "query"}])
     (run-query (find-corpus-config db corpus) query-str query-opts sort-opts filter-opts)
     db)))

(re-frame/register-handler
 :query-from
 (fn [db [_ from]]
   (let [{:keys [corpus query-opts sort-opts filter-opts]} (get-in db [:settings :query])
         {query-str :query-str} (current-results db)
         query-opts (if from (assoc query-opts :from from) query-opts)]
     (re-frame/dispatch [:start-throbbing :results-frame])
     (run-query (find-corpus-config db corpus) query-str query-opts sort-opts filter-opts)
     db)))

(re-frame/register-handler
 :query-range
 (fn [db [_ direction]]
   (let [results-summary (current-results db)]
     (when-not (empty? results-summary)       ;return if there hasn't been a query yet
       (let [{:keys [corpus sort-opts filter-opts query-opts]} (get-in db [:settings :query])
             {page-size :page-size :as query-opts} query-opts
             {query-str :query-str query-size :query-size {from :from to :to} :page} results-summary
             [from to] (case direction
                         :next (pager-next query-size page-size to)
                         :prev (pager-prev query-size page-size from))
             query-opts (assoc query-opts :from from :page-size (- to from))]
         (re-frame/dispatch [:start-throbbing :results-frame])
         (run-query (find-corpus-config db corpus) query-str query-opts sort-opts filter-opts)))
     db)))

(re-frame/register-handler
 :query-sort
 (fn [db _]
   (let [{:keys [corpus query-opts sort-opts filter-opts]} (get-in db [:settings :query])
         {query-str :query-str query-size :query-size {from :from to :to} :page} (current-results db)
         corpus-config (find-corpus-config db corpus)]
     (re-frame/dispatch [:start-throbbing :results-frame])
     (query-sort (ensure-corpus corpus-config) query-str query-opts sort-opts filter-opts)
     db)))

(re-frame/register-handler
 :update-hit
 standard-middleware
 (fn [db [_ {:keys [hit id]}]]
   (let [{doc-id :doc-id} (parse-hit-id id)
         active-project (get-in db [:session :active-project])
         start (->> hit first :id parse-token-id :id)
         end (->> hit last :id parse-token-id :id)]
     (if-let [current-hit (get-in db [:projects active-project :session :query :results-by-id id])]
       (do (re-frame/dispatch [:fetch-annotations [{:start start :end end :hit-id id :doc doc-id}]])
           (assoc-in db [:projects active-project :session :query :results-by-id id :hit] hit))
       (do (timbre/warn (format "Event :update-hit but coultn't find hit id [%s]" (str id)))
           db)))))

(defn update-hit [{:keys [hit id] :as hit-map}]
  (re-frame/dispatch [:update-hit hit-map]))

(re-frame/register-handler
 :expand-hit
 standard-middleware
 (fn [db [_ id dir]]
   (let [{:keys [corpus]} (get-in db [:settings :query])
         corpus (ensure-corpus (find-corpus-config db corpus))
         active-project (get-in db [:session :active-project])
         {:keys [hit]} (get-in db [:projects active-project :session :query :results-by-id id])
         [left match right] (partition-by :match hit)
         words-left (if (= dir :left) (inc (count left)) (count left))
         words-right (if (= dir :right) (inc (count right)) (count right))]
     (query-hit corpus id {:words-left words-left :words-right words-right} update-hit))
   db))

(re-frame/register-handler
 :fetch-snippet
 (fn [db [_ hit-id {user-delta :snippet-delta dir :dir}]]
   (let [{:keys [snippet-opts corpus]} (get-in db [:settings :query])
         {snippet-delta :snippet-delta} snippet-opts
         snippet-opts (assoc snippet-opts :snippet-delta (or user-delta snippet-delta))
         corpus (ensure-corpus (find-corpus-config db corpus))         
         {query-str :query-str} (current-results db)]
     (when-not dir                      ;only register first request
       (re-frame/dispatch
        [:register-user-project-event {:data {:hit-id hit-id :corpus corpus} :type "snippet"}]))
     (snippet corpus query-str snippet-opts hit-id dir)
     db)))
