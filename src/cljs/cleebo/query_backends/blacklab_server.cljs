(ns cleebo.query-backends.blacklab-server
  (:require [cleebo.ajax-jsonp :refer [jsonp]]
            [cleebo.query-backends.protocols :refer [Corpus handle-query]]))

(defn bl-server-url
  "builds the blacklab server url"
  [server web-service corpus-name & {:keys [resource] :or {resource "hits"}}]
  (str "http://" server "/" web-service "/" corpus-name "/" resource "?"))

(defn join-params [& params]
  (apply str (interpose ":" (filter identity params))))

(defn parse-sort-opts
  "transforms internal app sort data into a single blacklab-server sort string"
  [ms]
  (map (fn [{:keys [position attribute facet]}]
         (if (= position "match")
           (join-params "hit" attribute facet)
           (join-params position attribute facet)))
       ms))

(defn parse-filter-opts
  "transforms internal app filter data into a single blacklab-server filter string"
  [ms]
  (map (fn [{:keys [attribute value]}]
         (join-params attribute value))
       ms))

(defn bl-server-sort-str
  "builds the blacklab sort string from param maps"
  [sort-opts filter-opts]
  (apply str (interpose "," (concat (parse-sort-opts sort-opts) (parse-filter-opts filter-opts)))))

(defn parse-hit-id
  [hit-id]
  (let [[doc-id hit-start hit-end] (clojure.string/split hit-id #"\.")]
    {:doc-id doc-id :hit-start hit-start :hit-end hit-end}))

(deftype BlacklabServerCorpus [corpus-name server web-service]
  Corpus
  (query [this query-str {:keys [context from page-size] :as query-opts}]
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

  (snippet [this query-str {:keys [snippet-size] :as query-opts} hit-id]
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

(comment
  (def corpus (BlacklabServerCorpus. "mbg-index-small" "localhost:8080" "blacklab-server-1.3.4"))
  (query corpus "[word=\"a\"]" {:context 10 :from 0 :page-size 10}))
