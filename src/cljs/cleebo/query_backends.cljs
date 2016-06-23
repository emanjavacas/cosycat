(ns cleebo.query-backends
  (:require [re-frame.core :as re-frame]
            [cleebo.ajax-jsonp :refer [jsonp]]
            [ajax.core :refer [GET]]))

(defprotocol Corpus
  "abstraction over different corpus query engine services"
  (query [corpus query-str query-map]
    "retrieve `query-str` and retrieves `page-size` hits starting `from` with `context`
    `query-map` {:page-size int :from int :context int}")
  (query-sort [corpus query-str query-map sort-match-map sort-context-map filter-map]
    "retrieve a query on `query-str` & query opts `query-map`:
    `sort-match-map`   {:attribute attribute :facet facet}
    `sort-context-map` {:attribute attribute :facet facet}
    `filter-map`       {:attribute \"(textType|genre)\" :value \"(sermon|history)\"}
     opts: attribute (word|pos|lemma); facet (sensitive|insensitive)")
  (snippet [corpus query-str query-map hit-id]
    "fetches text surrounding a given query hit identified by `hit-id`"))

(defprotocol DataNormalization
  (handler-data [corpus data]
    "transforms success payload to app internal data structure")
  (error-handler-data [corpus data]
    "transforms error payload to app internal data structure"))

(defn handler [data]
  (.log js/console "SUCCESS!" data))

(defn error-handler [data]
  (.log js/console "ERROR!" data))

(deftype BlacklabCorpus [corpus-name]
  Corpus
  (query [this query-str {:keys [context from page-size] :as query-map}]
    (GET "/blacklab"
         {:params {:corpus corpus-name
                   :query-str (js/encodeURIComponent query-str)
                   :context context
                   :from from
                   :size (+ from page-size)
                   :route :query}
          :handler #(handler (handler-data this %))
          :error-handler #(error-handler (error-handler-data this %))}))

  (query-sort [this query-str
               {context :context from :from page-size :page-size :as query-map}
               {match-attr :attribute match-facet :facet :as sort-match-map}
               {context-attr :attribute context-facet :facet :as sort-context-map}
               {filter-attr :attribute filter-val :value :as filter-map}]
    (GET "/blacklab"
         {:params {:corpus corpus-name
                   :context context
                   :from from
                   :size (+ from page-size)
                   :route :sort-query
                   :sort-map {:criterion context-attr :attribute match-attr}}
          :handler #(handler (handler-data this %))
          :error-handler #(error-handler (error-handler-data this %))}))

  (snippet [this query-str {:keys [snippet-size] :as snippet-map} hit-id]
    (GET "/blacklab"
         {:params {:corpus corpus-name
                   :query-str query-str
                   :snippet-size snippet-size
                   :route :snippet}
          :handler #(handler (handler-data this %))
          :error-handler #(error-handler (error-handler-data this %))}))

  DataNormalization
  (handler-data [corpus data] (identity data))
  (error-handler-data [corpus data] (identity data)))

;; (def new-corpus (->BlacklabCorpus "mbg-small"))
;; (query new-corpus "\"a\"" {:context 10 :from 0 :page-size 5})

(defn bl-server-url
  "builds the blacklab server url"
  [server web-service corpus-name & {:keys [resource] :or {resource "hits"}}]
  (str "http://" server "/" web-service "/" corpus-name "/" resource "?"))

(defn parse-opts-map [opts-map ordered-keys & required-keys]
  (when-not (empty? opts-map)
    (do (assert (= (count required-keys) (count (select-keys opts-map required-keys))))
        (apply str (interpose ":" (filter identity (select-keys opts-map ordered-keys)))))))

(defn bl-server-sort-str
  "builds the blacklab sort string from param maps"
  [sort-match-map sort-context-map filter-map]
  (let [match-str (str "hit:" (parse-opts-map sort-match-map [:attribute :facet] :attribute))
        context-str (parse-opts-map sort-context-map [:attribute :facet] :attribute)
        filter-str (parse-opts-map filter-map [:attribute :value] :attribute :value)]
    (apply str (interpose "," (remove empty? [match-str context-str filter-str])))))

(defn parse-hit-id [hit-id]
  (let [[doc-id hit-start hit-end] (clojure.string/split hit-id #"\.")]
    {:doc-id doc-id :hit-start hit-start :hit-end hit-end}))

(deftype BlacklabServerCorpus [corpus-name server web-service]
  Corpus
  (query [this query-str {:keys [context from page-size] :as query-map}]
    (jsonp (bl-server-url server web-service corpus-name)
           {:params {:patt query-str
                     :wordsaroundhit context
                     :first from
                     :number page-size
                     :jsonp "callback"}
            :handler #(handler (handler-data this %))
            :error-handler #(error-handler (error-handler-data this %))}))

  (query-sort [this query-str
               {context :context from :from page-size :page-size :as query-map}
               {match-attr :attribute match-facet :facet :as sort-match-map}
               {context-attr :attribute context-facet :facet :as sort-context-map}
               {filter-attr :attribute filter-val :value :as filter-map}]
    (let [sort-str (bl-server-sort-str sort-match-map sort-context-map filter-map)]
      (jsonp (bl-server-url server web-service corpus-name)
             {:params {:patt (js/encodeURIComponent query-str)
                       :wordsaroundhit context
                       :first from
                       :number page-size
                       :sort sort-str
                       :jsonp "callback"}
              :handler #(handler (handler-data this %))
              :error-handler #(error-handler (error-handler-data this %))})))

  (snippet [this query-str {:keys [snippet-size] :as query-map} hit-id]
    (let [{:keys [doc-id hit-start hit-end]} (parse-hit-id hit-id)]
      (jsonp (bl-server-url server web-service corpus-name
                            :resource (str "docs/" doc-id "snippet"))
             {:params {:wordsaroundhit snippet-size
                       :hitstart hit-start
                       :hitend hit-end
                       :jsonp "callback"}})))
  DataNormalization
  (handler-data [corpus data] (identity data))
  (error-handler-data [corpus data] (identity data)))

(deftype CQPServerCorpus [corpus-name])

;; (def corpus (BlacklabServerCorpus. "mbg-index-small" "localhost:8080" "blacklab-server-1.3.4"))
;; (query corpus "[word=\"a\"]" {:context 10 :from -1 :page-size 10})
