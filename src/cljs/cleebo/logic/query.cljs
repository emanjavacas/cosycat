(ns cleebo.logic.query
  (:require [re-frame.core :as re-frame]
            [ajax.core :refer [GET]]
            [taoensso.timbre :as timbre])
  (:require-macros [cleebo.env :as env :refer [cljs-env]]))

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

(defn which-endpoint? [corpus]
  (let [cqp-corpora (cljs-env :cqp :corpora)
        bl-corpora  (cljs-env :blacklab :corpora)]
    (cond (some #{corpus} cqp-corpora) "cqp"
          (some #{corpus} bl-corpora)  "blacklab"
          :else (throw (js/Error "Unknown corpus")))))

(defn query
  "will need to support 'from' for in-place query-opts change"
  [{:keys [query-str corpus context size from] :or {from 0} :as query-args}]
  (GET (which-endpoint? corpus)
       {:handler query-results-handler
        :error-handler error-handler
        :params {:query-str query-str
                 :corpus corpus
                 :context context
                 :from from
                 :size size
                 :route :query}}))

(defn query-range [corpus from to context]
  (GET (which-endpoint? corpus)
       {:handler query-results-handler
        :error-handler error-handler
        :params {:corpus corpus
                 :from from
                 :to to
                 :context context
                 :route :query-range}}))

(defn query-sort [corpus from to context criterion prop-name sort-type]
  (GET (which-endpoint? corpus)
       {:handler query-results-handler
        :error-handler error-handler
        :params {:corpus corpus
                 :from from
                 :to to
                 :context context
                 :sort-map {:criterion criterion
                            :prop-name prop-name}
                 :route sort-type}}))
