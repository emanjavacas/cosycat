(ns cleebo.backend.handlers.query
  (:require [re-frame.core :as re-frame]
            [cleebo.backend.middleware :refer [standard-middleware no-debug-middleware]]
            [cleebo.backend.db :refer [default-project-session]]
            [cleebo.query-backends.core :refer [ensure-corpus]]
            [cleebo.query-backends.protocols :refer [query query-sort snippet]]
            [cleebo.query-parser :refer [missing-quotes]]
            [cleebo.utils :refer [filter-marked-hits ->int]]
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

(defn merge-fn
  "on new results, update current results leaving untouched those that are marked"
  [results & {:keys [has-marked?]}]
  (fn [old-results]
    (merge (zipmap (map :id results) results) ;normalize results
           (filter-marked-hits old-results :has-marked? has-marked?))))

(defn fetch-annotations-params [results]
  (some->> (seq (for [{hit :hit id :id} results] ;seq; avoid empty seq
                  [(->int (:id (first hit))) (->int (:id (last hit))) id]))
           (apply map vector)
           (zipmap [:starts :ends :hit-ids])))

(re-frame/register-handler
 :set-query-results
 standard-middleware
 (fn [db [_ {:keys [results-summary results status]}]]
   (re-frame/dispatch [:stop-throbbing :results-frame])
   (re-frame/dispatch [:fetch-annotations (fetch-annotations-params results)])
   (let [active-project (get-in db [:session :active-project])
         path-fn (fn [key] [:projects active-project :session :query key])
         merge-old-results (merge-fn results :has-marked? true)]
     (-> db
         (assoc-in [:projects active-project :session :status] status)
         (update-in (path-fn :results-summary) merge results-summary)
         (assoc-in (path-fn :results) (map :id results))
         (update-in (path-fn :results-by-id) merge-old-results)))))

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
  (some #(when (= corpus-name (:name %)) %) (db :corpora)))

(defn current-results [db]
  (let [active-project (get-in db [:session :active-project])
        project (get-in db [:projects active-project])]
    (get-in project [:session :query :results-summary])))

(defn empty-before [s n]
  (count (filter #(= % " ") (subs s n))))

(defn check-query [query-str]
  (let [{status :status at :at} (missing-quotes query-str)
        at (+ at (empty-before query-str at))]
    (case status
      :mismatch (re-frame/dispatch
                 [:query-error
                  {:code "Query string error"
                   :message (str "Query: " query-str " ; has error at position: " at ".")}])
      :finished true)))

(re-frame/register-handler
 :query
 (fn [db [_ query-str]]
   (let [{query-opts :query-opts corpus-name :corpus} (get-in db [:session :settings :query])
         corpus-config (find-corpus-config db corpus-name)]
     (when (check-query query-str)
       (do (re-frame/dispatch [:start-throbbing :results-frame])
           (re-frame/dispatch [:start-throbbing :fetch-annotations])
           ;; add update
           (re-frame/dispatch
            [:register-history [:internal-events]
             {:type :query
              :payload {:query-str query-str :corpus corpus-name}}])
           (query (ensure-corpus corpus-config) query-str query-opts)))
     db)))

(re-frame/register-handler
 :query-range
 (fn [db [_ direction]]
   (let [query-settings (get-in db [:session :settings :query])
         {{page-size :page-size :as query-opts} :query-opts corpus-name :corpus} query-settings
         {query-str :query-str query-size :query-size {from :from to :to} :page} (current-results db)
         corpus-config (find-corpus-config db corpus-name)
         [from to] (case direction
                     :next (pager-next query-size page-size to)
                     :prev (pager-prev query-size page-size from))]
     (re-frame/dispatch [:start-throbbing :results-frame])
     (query (ensure-corpus corpus-config)
            query-str
            (assoc query-opts :from from :page-size (- to from)))
     db)))

(re-frame/register-handler
 :query-sort
 (fn [db _]
   (let [{:keys [corpus query-opts sort-opts filter-opts]} (get-in db [:session :settings :query])
         {query-str :query-str query-size :query-size {from :from to :to} :page} (current-results db)
         corpus-config (find-corpus-config db corpus)]
     (re-frame/dispatch [:start-throbbing :results-frame])
     (query-sort (ensure-corpus corpus-config) query-str query-opts sort-opts filter-opts)
     db)))

(re-frame/register-handler
 :fetch-snippet
 (fn [db [_ hit-id {user-snippet-delta :snippet-delta dir :dir}]]
   (let [{{snippet-delta :snippet-delta :as snippet-opts} :snippet-opts
          corpus-name :corpus} (get-in db [:session :settings :query])
         {query-str :query-str} (current-results db)
         corpus (ensure-corpus (find-corpus-config db corpus-name))
         snippet-opts (assoc snippet-opts :snippet-delta (or user-snippet-delta snippet-delta))]
     ;; add update
     (when-not dir                      ;only register first request
       (re-frame/dispatch
        [:register-history [:internal-events]
         {:type :fetch-snippet
          :payload {:query-str query-str :corpus corpus :hit-id hit-id}}]))
     (snippet corpus query-str snippet-opts hit-id dir)
     db)))
