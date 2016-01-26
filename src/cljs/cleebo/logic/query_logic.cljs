(ns cleebo.logic.query-logic
  (:require [re-frame.core :as re-frame]
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

;;; handlers
(defn error-handler [{:keys [status status-content]}]
  (re-frame/dispatch
   [:set-session
    [:query-results :status]
    {:status status :status-content status-content}]))

(defn query-results-handler [data]
  (let [{query-size :query-size} data
        data (if (zero? query-size) (assoc data :results nil) data)]
    (re-frame/dispatch [:set-query-results data])
    (re-frame/dispatch [:stop-throbbing :results-frame])))

(defn query
  "will need to support 'from' for in-place query-opts change"
  [{:keys [query-str corpus context size from] :or {from 0} :as query-args}]
  (GET "/query"
       {:handler query-results-handler
        :error-handler error-handler
        :params {:query-str query-str
                 :corpus corpus
                 :context context
                 :from from
                 :size size}}))

(defn query-range [{:keys [corpus from to context sort-map]}]
  (GET "/range"
       {:handler query-results-handler
        :error-handler error-handler
        :params {:corpus corpus
                 :from from
                 :to to
                 :context context
                 :sort-map sort-map}}))

;;; triggers
;;; sort
(defn range-trigger
  [query-results-atom query-opts-atom &
   {:keys [overwrite criterion-atom prop-name-atom sort-type]}]
  (let [{:keys [query-size from]} @query-results-atom
        {:keys [corpus context size]} @query-opts-atom
        sort-map (if (and criterion-atom prop-name-atom)
                   {:criterion @criterion-atom
                    :prop-name @prop-name-atom
                    :sort-type sort-type})]
    (re-frame/dispatch [:start-throbbing :results-frame])
    (query-range (merge {:corpus corpus
                         :from from
                         :to (+ from size)
                         :context context
                         :sort-map sort-map}
                        overwrite))))
