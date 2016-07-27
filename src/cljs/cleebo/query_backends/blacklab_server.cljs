(ns cleebo.query-backends.blacklab-server
  (:require [cleebo.ajax-jsonp :refer [jsonp]]
            [cleebo.query-backends.protocols :as p]
            [cleebo.utils :refer [->int]]
            [taoensso.timbre :as timbre]
            [cljs.core.async :refer [chan >! <! put! close! timeout sliding-buffer take!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn bl-server-url
  "builds the blacklab server url"
  [server web-service index & {:keys [resource] :or {resource "hits"}}]
  (str "http://" server "/" web-service "/" index "/" resource))

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

(defn sub-hit [{:keys [punct id word]} & {:keys [is-match?]}]
  (mapv (fn [token-word token-id]
          (if is-match?
            {:word token-word :id token-id :match true}
            {:word token-word :id token-id}))
        word
        id))

(defn normalize-meta [num doc]
  (assoc doc :num num))

(defn normalize-bl-hit
  [hit num doc]
  (let [{left :left match :match right :right doc-id :docPid start :start end :end} hit]    
    {:hit (concat (sub-hit left) (sub-hit match :is-match? true) (sub-hit right))
     :id (apply str (interpose "." [doc-id start end]))
     :meta (normalize-meta num doc)}))

(defn ->results-summary
  [{{from :first corpus :indexname query-str :patt num-hits :number} :searchParam
    query-size :numberOfHits query-time :searchTime has-next :windowHasNext}]
  {:page {:from (->int from) :to (+ (->int from) (->int num-hits))}
   :query-size query-size
   :query-str query-str
   :query-time query-time
   :has-next has-next
   :corpus corpus})

(defn ->results [doc-infos hits from]
  (vec (map-indexed
        (fn [idx {doc-id :docPid :as hit}]
          (let [num (+ idx (->int from))
                doc (get doc-infos (keyword doc-id))]
            (normalize-bl-hit hit num doc)))
        hits)))

(defn parse-info-data [data]
  (let [parser (js/DOMParser.)]
    (js->clj (.parseFromString parser data "text/xml"))))

(def bl-default-params
  {:maxcount 100000
   :waitfortotal "no"})

(declare on-counting clear-timeout maybe-reset-last-action set-last-action get-sort-params)

(deftype BlacklabServerCorpus [index server web-service on-counting-cb last-action timeout-ids]
  p/Corpus
  (p/query [this query-str {:keys [context from page-size] :as query-opts}]
    (clear-timeout timeout-ids)
    (maybe-reset-last-action last-action {:action-id query-str})
    (.log js/console "last-action" @last-action)
    (p/handle-query
     this (bl-server-url server web-service index)
     (merge {:patt query-str
             :wordsaroundhit context
             :first from
             :number page-size
             :jsonp "callback"}
            (when-let [sort-params (get-sort-params last-action)]
              {:sort (apply bl-server-sort-str sort-params)})
            bl-default-params)
     :method jsonp))

  (p/query-sort [this query-str {:keys [context from page-size]} sort-opts filter-opts]
    (let [sort-str (bl-server-sort-str sort-opts filter-opts)]
      (set-last-action last-action {:action-id query-str :action :sort :params [sort-opts filter-opts]})
      (p/handle-query
       this (bl-server-url server web-service index)
       (merge {:patt query-str
               :wordsaroundhit context
               :first from
               :number page-size
               :sort sort-str
               :jsonp "callback"}
              bl-default-params)
       :method jsonp)))

  (p/snippet [this query-str {:keys [snippet-size] :as query-opts} hit-id]
    (let [{:keys [doc-id hit-start hit-end]} (parse-hit-id hit-id)]
      (p/handle-query
       this (bl-server-url server web-service index :resource (str "docs/" doc-id "snippet"))
       (merge {:wordsaroundhit snippet-size
               :hitstart hit-start
               :hitend hit-end
               :jsonp "callback"}
              bl-default-params)
       :method jsonp)))
  
  (p/transform-data [corpus data]
    (let [{{:keys [message code] :as error} :error :as cljs-data} (js->clj data :keywordize-keys true)
          uri (bl-server-url server web-service index)]
      (if error
        {:message message :code code}
        (let [{summary :summary hits :hits doc-infos :docInfos} cljs-data
              {{from :first :as params} :searchParam counting? :stillCounting} summary]
          (when counting? (on-counting timeout-ids {:uri uri :params params :callback on-counting-cb}))
          {:results-summary (->results-summary summary)
           :results (->results doc-infos hits from)
           :status {:status :ok}}))))
 
  (p/transform-error-data [corpus data]
    (identity data))

  (p/get-corpus-info [corpus]
    (let [uri (bl-server-url server web-service index :resource "")]
      (jsonp uri {:params {}
                  :handler #(let [parsed-data (parse-info-data %)] (p/handle-info parsed-data))
                  :error-handler #(.log js/console)}))))

(defn clear-timeout [timeout-ids]
  (doseq [timeout-id @timeout-ids]
    (js/clearTimeout timeout-id))
  (reset! timeout-ids []))

(defn on-counting
  [timeout-ids {:keys [uri params callback retry-count retried-count]
                 :or {retry-count 5 retried-count 0} :as opts}]
  (when (< retried-count retry-count)
    (->> (js/setTimeout
          (fn []
            (jsonp uri
                   {:params (assoc params :number 0 :jsonp "callback")
                    :error-handler identity
                    :handler 
                    #(let [{error :error :as data} (js->clj % :keywordize-keys true)]
                       (if-not error
                         (let [{{query-size :numberOfHits counting? :stillCounting} :summary} data]
                           (timbre/debug counting? query-size)
                           (callback query-size)
                           (when counting? (on-counting timeout-ids (update opts :retried-count inc))))
                         (timbre/info "Error occurred when requesting counted hits")))}))
          (+ 1000 (* 1500 retried-count)))
         (swap! timeout-ids conj))))

(defn maybe-reset-last-action [last-action {:keys [action-id]}]
  (if-not (= action-id (:action-id @last-action))
    (reset! last-action {})))

(defn get-sort-params [last-action]
  (let [{:keys [action params]} @last-action]
    (when (= action :sort)
      params)))

(defn set-last-action [last-action {:keys [action-id action params]}]
  (swap! last-action assoc :action action :params params :action-id action-id))

(defn make-blacklab-server-corpus
  [{:keys [index server web-service on-counting-callback] :or {on-counting-callback identity} :as args}]
  (let [last-action (atom {})
        timeout-ids (atom [])]
    (->BlacklabServerCorpus index server web-service on-counting-callback last-action timeout-ids)))

;; (def mbg-corpus
;;   (make-blacklab-server-corpus
;;    {:index "mbg-index-small" :server "mbgserver.uantwerpen.be:8080" :web-service "blacklab-server-1.4-SNAPSHOT"}))
;; (p/query mbg-corpus "[word=\"was\"]" {:context 5 :from 0 :page-size 15})
;(get-corpus-info mbg-corpus)

