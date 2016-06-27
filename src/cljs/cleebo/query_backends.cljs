(ns cleebo.query-backends
  (:require [re-frame.core :as re-frame]
            [cleebo.ajax-jsonp :refer [jsonp]]
            [ajax.core :refer [GET]]))

(defn handler [data] (.log js/console "SUCCESS!" data))
(defn error-handler [data] (.log js/console "ERROR!" data))

(defprotocol Corpus
  "abstraction over different corpus query engine services"
  (query [corpus query-str query-map]
    "retrieve `query-str` and retrieves `page-size` hits starting `from` with `context`
    `query-map` {:page-size int :from int :context int}")
  (query-sort [corpus query-str query-map sort-opts filter-opts]
    "retrieve a query on `query-str` & query opts `query-map`:
    `sort-opts`   [{:position \"(match|left|right)\" :attribute attribute :facet facet}]
    `filter-opts` [{:attribute \"(textType|genre)\" :value \"(sermon|history)\"}]
     opts: attribute (word|pos|lemma); facet (sensitive|insensitive)")
  (snippet [corpus query-str query-map hit-id]
    "fetches text surrounding a given query hit identified by `hit-id`")
  (handler-data [corpus data]
    "transforms success payload to app internal data structure")
  (error-handler-data [corpus data]
    "transforms error payload to app internal data structure"))

(defn handle-query [corpus url params & {:keys [method] :or {method GET}}]
  (method url {:params params :handler (fn [data] (handler (handler-data corpus data)))}))

(defn bl-parse-sort-opts [sort-opts]
  (reduce (fn [acc {:keys [position attribute facet]}]
            (if (= "match" position)
              (assoc acc :criterion position :attribute (or attribute "word"))))
          {}
          sort-opts))

(deftype BlacklabCorpus [corpus-name]
  Corpus
  (query [this query-str {:keys [context from page-size] :as query-map}]
    (handle-query
     this "/blacklab"
     {:corpus corpus-name
      :query-str (js/encodeURIComponent query-str)
      :context context
      :from from
      :size (+ from page-size)
      :route :query}))

  (query-sort [this query-str {:keys [context from page-size]} sort-opts filter-opts]
    (handle-query
     this "/blacklab"
     {:corpus corpus-name
      :context context
      :from from
      :size (+ from page-size)
      :route :sort-query
      :sort-map (bl-parse-sort-opts sort-opts)}))

  (snippet [this query-str {:keys [snippet-size] :as snippet-map} hit-id]
    (handle-query
     this "/blacklab"
     {:corpus corpus-name
      :query-str query-str
      :snippet-size snippet-size
      :route :snippet}))
  
  (handler-data [corpus data] (identity data))
  (error-handler-data [corpus data] (identity data)))

;; (def new-corpus (->BlacklabCorpus "mbg-small"))
;; (query new-corpus "\"a\"" {:context 10 :from 0 :page-size 5})

(defn bl-server-url
  "builds the blacklab server url"
  [server web-service corpus-name & {:keys [resource] :or {resource "hits"}}]
  (str "http://" server "/" web-service "/" corpus-name "/" resource "?"))

(defn join-params [& params]
  (apply str (interpose ":" (filter identity params))))

(defn parse-sort-opts [ms]
  (map (fn [{:keys [position attribute facet]}]
         (if (= position "match")
           (join-params "hit" attribute facet)
           (join-params position attribute facet)))
       ms))

(defn parse-filter-opts [ms]
  (map (fn [{:keys [attribute value]}]
         (join-params attribute value))
       ms))

(defn bl-server-sort-str
  "builds the blacklab sort string from param maps"
  [sort-opts filter-opts]
  (apply str (interpose "," (concat (parse-sort-opts sort-opts) (parse-filter-opts filter-opts))))
  
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
    (handle-query
     this (bl-server-url server web-service corpus-name)
     {:patt query-str
      :wordsaroundhit context
      :first from
      :number page-size
      :jsonp "callback"}
     :method jsonp))

  (query-sort [this query-str {:keys [context from page-size]} sort-opts filter-opts]
    (let [sort-str (bl-server-sort-str sort-opts filter-opts)]
      (handle-query
       this (bl-server-url server web-service corpus-name)
       {:patt (js/encodeURIComponent query-str)
        :wordsaroundhit context
        :first from
        :number page-size
        :sort sort-str
        :jsonp "callback"}
       :method jsonp)))

  (snippet [this query-str {:keys [snippet-size] :as query-map} hit-id]
    (let [{:keys [doc-id hit-start hit-end]} (parse-hit-id hit-id)]
      (handle-query
       this (bl-server-url server web-service corpus-name :resource (str "docs/" doc-id "snippet"))
       {:wordsaroundhit snippet-size
        :hitstart hit-start
        :hitend hit-end
        :jsonp "callback"}
       :method jsonp)))
  
  (handler-data [corpus data] (identity data))
  (error-handler-data [corpus data] (identity data)))

(deftype CQPServerCorpus [corpus-name])

;; (def corpus (BlacklabServerCorpus. "mbg-index-small" "localhost:8080" "blacklab-server-1.3.4"))
;; (query corpus "[word=\"a\"]" {:context 10 :from -1 :page-size 10})
