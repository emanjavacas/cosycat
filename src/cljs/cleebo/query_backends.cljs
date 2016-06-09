(ns cleebo.query-backends)

(defprotocol QueriableCorpus
  "abstraction over different corpus query engine services"
  (query [corpus query-str {:keys [window from page-size] :as query-opts}]
    "runs `query-str` and retrieves `page-size` hits starting from 0"))

(defprotocol SortableCorpus
  (query-sort [corpus query-str {:keys [criterion attribute reverse] :as sort-opts}
               {:keys [window from page-size] :as query-opts}]
    "runs a query on `query-str` sorted by `criterion` (left, right-context, ...),
    `attribute` (word, POS-tag, etc...) and `reverse` (direction)"))

(defprotocol SnippetableCorpus
  (snippet [corpus query-str hit-id {:keys [window] :as snippet-opts}]
    "fetches text surrounding a given query hit identified by `hit-id`"))

(deftype BlacklabCorpus [corpus-name]
  QueriableCorpus
  (query []))

(deftype BlacklabServerCorpus [corpus-name])

(deftype CQPServerCorpus [corpus-name])
