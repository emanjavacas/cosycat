(ns cleebo.backend.handlers.query
  (:require [re-frame.core :as re-frame]
            [cleebo.backend.middleware :refer [standard-middleware no-debug-middleware]]
            [cleebo.utils :refer [filter-marked-hits]]
            [ajax.core :refer [GET]]
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

(defn error-handler
  [source-component]
  (fn [{:keys [status status-content]}]
    (re-frame/dispatch [:stop-throbbing source-component])
    (re-frame/dispatch
     [:set-session
      [:query-results :status]
      {:status status :status-content status-content}])))

(defn keywordify-results [results]
  (into {} (map (juxt :id identity) results)))

(defn merge-fn
  "on new results, update current results leaving untouched those that are marked"
  [results & {:keys [has-marked?]}]
  (fn [old-results]
    (merge (keywordify-results results)
           (filter-marked-hits old-results :has-marked? has-marked?))))

(defn results-handler
  "general success handler for query routes
  (:query, :query-range :query-sort :query-refresh).
  Accepts additional callbacks `extra-work` that are passed the incoming data."
  [source-component & extra-work]
  (fn [data]
    (if (string? data)
      (.assign js/location "/logout")
      (do (doall (map #(% data) extra-work))
          (re-frame/dispatch [:set-query-results data])
          (re-frame/dispatch [:stop-throbbing source-component])))))

(re-frame/register-handler
 :set-query-results
 standard-middleware
 (fn [db [_ & [{:keys [results from to] :as data}]]]
   (let [query-results (dissoc data :results)
         merge-old-results (merge-fn results :has-marked? true)]
     (-> db
         (update-in [:session :query-results] merge query-results)
         (assoc-in [:session :results] (map :id results))
         (update-in [:session :results-by-id] merge-old-results)))))

(re-frame/register-handler
 :reset-query-results
 standard-middleware
 (fn [db _]
   (assoc-in db [:session :results-by-id] {})))

(re-frame/register-handler
 :query
 standard-middleware
 (fn [db [_ query-str source-component]]
   (let [{{:keys [corpus context size]} :query-opts
          {:keys [from]}                :query-results} (:session db)]
     (re-frame/dispatch [:start-throbbing source-component])
     (GET "/blacklab"
          {:handler (results-handler
                     source-component
                     #(re-frame/dispatch [:reset-query-results]))
           :error-handler (error-handler source-component)
           :params {:query-str query-str
                    :corpus corpus
                    :context context
                    :from 0
                    :size size
                    :route :query}})
     db)))

(re-frame/register-handler
 :query-range
 standard-middleware
 (fn [db [_ direction source-component]]
   (let [{{:keys [corpus context size]} :query-opts
          {:keys [from to query-size]}  :query-results} (:session db)
         [from to] (case direction
                     :next (pager-next query-size size to)
                     :prev (pager-prev query-size size from))]
     (when (and (pos? (inc from)) (<= to query-size))
       (re-frame/dispatch [:start-throbbing source-component])
       (GET "/blacklab"
            {:handler (results-handler source-component)
             :error-handler (error-handler source-component)
             :params {:corpus corpus
                      :context context
                      :from from
                      :to to
                      :route :query-range}}))
     db)))

(re-frame/register-handler
 :query-refresh
 standard-middleware
 (fn [db [_ source-component]]
   (let [{{:keys [corpus context size]} :query-opts
          {:keys [from to query-size]}  :query-results} (:session db)
         to (min query-size (+ from size))]
     (re-frame/dispatch [:start-throbbing source-component])
     (GET "/blacklab"
          {:handler (results-handler source-component)
           :error-handler (error-handler source-component)
           :params {:corpus corpus
                    :context context
                    :from from
                    :to to
                    :route :query-range}})
     db)))

(re-frame/register-handler
 :query-sort
 standard-middleware
 (fn [db [_ route source-component]]
   (let [{{:keys [corpus context size criterion prop-name]} :query-opts
          {:keys [from]} :query-results} (:session db)]
     (re-frame/dispatch [:start-throbbing source-component])
     (GET "/blacklab"
          {:handler (results-handler source-component)
           :error-handler (error-handler source-component)
           :params {:corpus corpus
                    :context context
                    :from from
                    :to (+ from size)
                    :route route
                    :sort-map {:criterion criterion
                               :prop-name prop-name}}})
     db)))
