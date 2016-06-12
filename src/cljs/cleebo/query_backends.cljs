(ns cleebo.query-backends
  (:require [re-frame.core :as re-frame]
            [ajax.core :refer [GET]]))

(defprotocol QueriableCorpus
  "abstraction over different corpus query engine services"
  (query [corpus query-str query-opts-map]
    "runs `query-str` and retrieves `page-size` hits starting `from` via `handlers`"))

(defprotocol SortableCorpus
  (query-sort [corpus query-str sort-opts-map query-opts-map]
    "runs a query on `query-str` sorted by `criterion` (left, right-context, ...),
    `attribute` (word, POS-tag, etc...) and `reverse` (direction)"))

(defprotocol SnippetableCorpus
  (snippet [corpus query-str hit-id query-opts-map]
    "fetches text surrounding a given query hit identified by `hit-id`"))

(defprotocol DataNormalization
  (handler-data [corpus data]
    "transforms success payload to app internal data structure")
  (error-handler-data [corpus data]
    "transforms error payload to app internal data structure"))

(declare handler error-handler)

(deftype BlacklabCorpus [corpus-name]
  QueriableCorpus
  (query [this query-str {:keys [context from page-size]}]
    (GET "/blacklab"
         {:params {:corpus corpus-name
                   :query-str (js/encodeURIComponent query-str)
                   :context context
                   :from from
                   :to (+ from page-size)
                   :route :query}
          :handler #(handler (handler-data this %))
          :error-handler #(error-handler (error-handler-data this %))}))
  
  SortableCorpus
  (query-sort [this query-str {:keys [criterion attribute reverse]} {:keys [context from page-size]}]
    (GET "/blacklab"
         {:params {:corpus corpus-name
                   :context context
                   :from from
                   :to (+ from page-size)
                   :route :sort-query
                   :sort-map {:criterion criterion :attribute attribute}}
          :handler #(handler (handler-data this %))
          :error-handler #(error-handler (error-handler-data this %))}))

  SnippetableCorpus
  (snippet [this query-str hit-id {:keys [snippet-size]}]
    (GET "/blacklab"
         {:params {:corpus corpus-name
                   :query-str query-str
                   :snippet-size snippet-size
                   :route :snippet}
          :handler #(handler (handler-data this %))
          :error-handler #(error-handler (error-handler-data this %))}))

  DataNormalization
  (handler-data [corpus data])
  (error-handler-data [corpus data]))

(defn bl-server-url
  [server web-service corpus-name & {:keys [resource] :or {resource "hits"}}]
  (str "http://" server "/" web-service "/" corpus-name "/" resource "?"))

(defn bl-server-sort-str [criterion attribute reverse]
  (apply str (interpose "," (filter identity [(str "hit:" attribute) criterion]))))

(defn parse-hit-id [hit-id]
  (let [[doc-id hit-start hit-end] (clojure.string/split hit-id #"\.")]
    {:doc-id doc-id :hit-start hit-start :hit-end hit-end}))

(deftype BlacklabServerCorpus [corpus-name server web-service]
  QueriableCorpus
  (query [this query-str {:keys [context from page-size]}]
    (GET (bl-server-url server web-service corpus-name)
         {:params {:patt (js/encodeURIComponent query-str)
                   :wordsaroundhit context
                   :first from
                   :number page-size}
          :handler #(handler (handler-data this %))
          :error-handler #(error-handler (error-handler-data this %))}))
  
  SortableCorpus
  (query-sort [this query-str {:keys [criterion attribute reverse]} {:keys [context from page-size]}]
    (let [sort-str (bl-server-sort-str criterion attribute reverse)]
      (GET (bl-server-url server web-service corpus-name)
           {:params {:patt (js/encodeURIComponent query-str)
                     :wordsaroundhit context
                     :first from
                     :number page-size
                     :sort sort-str}
            :handler #(handler (handler-data this %))
            :error-handler #(error-handler (error-handler-data this %))})))

  SnippetableCorpus
  (snippet [this query-str hit-id {:keys [snippet-size]}]
    (let [{:keys [doc-id hit-start hit-end]} (parse-hit-id hit-id)]
      (GET (bl-server-url server web-service corpus-name :resource (str "docs/" doc-id "snippet"))
           {:params {:wordsaroundhit snippet-size
                     :hitstart hit-start
                     :hitend hit-end}}))))

(deftype CQPServerCorpus [corpus-name])
