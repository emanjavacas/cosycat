(ns cleebo.query-backends
  (:require [re-frame.core :as re-frame]
            [ajax.core :refer [GET]]))

(defprotocol QueriableCorpus
  "abstraction over different corpus query engine services"
  (query [corpus query-str query-opts-map handlers-map]
    "runs `query-str` and retrieves `page-size` hits starting `from` via `handlers`"))

(defprotocol SortableCorpus
  (query-sort [corpus query-str sort-opts-map query-opts-map handlers-maps]
    "runs a query on `query-str` sorted by `criterion` (left, right-context, ...),
    `attribute` (word, POS-tag, etc...) and `reverse` (direction)"))

(defprotocol SnippetableCorpus
  (snippet [corpus query-str hit-id query-opts-map handlers-map]
    "fetches text surrounding a given query hit identified by `hit-id`"))

(defprotocol DataNormalization
  (handler-data [corpus data]
    "transforms success payload to app internal data structure")
  (error-handler-data [corpus data]
    "transforms error payload to app internal data structure"))

(deftype BlacklabCorpus [corpus-name]
  QueriableCorpus
  (query [corpus query-str {:keys [context from page-size]}]
    (GET "/blacklab"
         {:params {:corpus corpus-name
                   :query-str (js/encodeURIComponent query-str)
                   :context context
                   :from from
                   :to (+ from page-size)
                   :route :query}
          :handler handler
          :error-handler error-handler}))
  
  SortableCorpus
  (query-sort
      [corpus query-str {:keys [criterion attribute reverse]} {:keys [context from page-size]}]
    (GET "/blacklab"
         {:params {:corpus corpus-name
                   :context context
                   :from from
                   :to (+ from size)
                   :route :sort-query
                   :sort-map {:criterion criterion :attribute attribute}}
          :handler handler
          :error-handler error-handler}))

  SnippetableCorpus
  (snippet [corpus query-str hit-id {:keys [snippet-size]}]
    (GET "/blacklab"
         {:params {:corpus corpus-name
                   :query-str corpus-str
                   :snippet-size snippet-size
                   :route :snippet}
          :handler handler
          :error-handler error-handler}))

  DataNormalization
  (handler-data [corpus data])
  (error-handler-data [corpus data]))

(deftype BlacklabServerCorpus [corpus-name]
  (GET ""))

(deftype CQPServerCorpus [corpus-name])
