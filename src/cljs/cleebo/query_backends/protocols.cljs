(ns cleebo.query-backends.protocols
  (:require [re-frame.core :as re-frame]
            [ajax.core :refer [GET]]))

(defprotocol Corpus
  "abstraction over different corpus query engine services"
  (query [corpus query-str query-opts]
    "retrieve `query-str` and retrieves `page-size` hits starting `from` with `context`
    `query-opts` {:page-size int :from int :context int}")
  (query-sort [corpus query-str query-opts sort-opts filter-opts]
    "retrieve a query on `query-str` & query opts `query-opts`:
    `sort-opts`   [{:position \"(match|left|right)\" :attribute attribute :facet facet}]
    `filter-opts` [{:attribute \"(textType|genre)\" :value \"(sermon|history)\"}]
     opts: attribute (word|pos|lemma); facet (sensitive|insensitive)")
  (snippet [corpus query-str query-opts hit-id]
    "fetches text surrounding a given query hit identified by `hit-id`")
  (handler-data [corpus data]
    "transforms success payload to app internal data structure")
  (error-handler-data [corpus data]
    "transforms error payload to app internal data structure"))

(defn handler
  "general handler called with normalized corpus query data"
  [{:keys [results results-summary error] :as payload}]
  (.log js/console error)
  (if error
    (re-frame/dispatch [:query-error error])
    (re-frame/dispatch [:set-query-results payload])))

(defn error-handler
  "general error handler called with normalized query error data"
  [data]
  (re-frame/dispatch
   [:query-error
    {:message "A Jsonp timeout error occurred. This can have various causes."
     :code "Unrecognized query error"}]))

(defn handle-query
  "wrapper for ajax/jsonp queries that simplifies protocol implementations"
  [corpus url params & {:keys [method] :or {method GET}}]
  (method url {:params params
               :handler (fn [data] (handler (handler-data corpus data)))
               :error-handler (fn [data] (error-handler (error-handler-data corpus data)))}))
