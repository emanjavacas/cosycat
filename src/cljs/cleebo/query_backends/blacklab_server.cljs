(ns cleebo.query-backends.blacklab-server
  (:require [ajax.core :refer [GET]]
            [cljs.core.async :refer [chan >! <! put! close! timeout sliding-buffer take!]]
            [cleebo.ajax-jsonp :refer [jsonp]]
            [cleebo.query-backends.protocols :as p]
            [cleebo.utils :refer [->int keywordify]]
            [cleebo.localstorage :refer [with-ls-cache]]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(declare ->BlacklabServerCorpus)

(defn make-blacklab-server-corpus
  [{:keys [index server web-service on-counting-callback] :or {on-counting-callback identity}}]
  (let [last-action (atom {}), timeout-ids (atom [])]
    (->BlacklabServerCorpus index server web-service on-counting-callback last-action timeout-ids)))

(def default-query-params
  {:maxcount 100000
   :waitfortotal "no"})

;;; parse cleebo args -> blacklab-server params
(declare bl-server-url bl-server-sort-str)

;;; normalize blacklab-out -> cleebo app-schemas
(declare parse-hit-id normalize-results normalize-results-summary normalize-corpus-info)

;;; handle counting callbacks
(declare on-counting clear-timeout)

;;; handle internal sort state (blacklab-server is stateless)
(declare maybe-reset-last-action set-last-action get-sort-opts)

(deftype BlacklabServerCorpus [index server web-service callback last-action timeout-ids]
  p/Corpus
  (p/query [this query-str {:keys [context from page-size] :as query-opts}]
    (clear-timeout timeout-ids)
    (maybe-reset-last-action last-action {:action-id query-str})
    (p/handle-query
     this (bl-server-url server web-service index)
     (merge {:patt query-str
             :wordsaroundhit context
             :first from
             :number page-size
             :jsonp "callback"}
            (when-let [sort-opts (get-sort-opts last-action)]
              {:sort (apply bl-server-sort-str sort-opts)})
            default-query-params)
     :method jsonp))

  (p/query-sort [this query-str {:keys [context from page-size]} sort-opts filter-opts]
    (let [sort-str (bl-server-sort-str sort-opts filter-opts)]
      (set-last-action last-action {:action-id query-str :action :sort :opts [sort-opts filter-opts]})
      (p/handle-query
       this (bl-server-url server web-service index)
       (merge {:patt query-str
               :wordsaroundhit context
               :first from
               :number page-size
               :sort sort-str
               :jsonp "callback"}
              default-query-params)
       :method jsonp)))

  (p/snippet [this query-str {:keys [snippet-size] :as query-opts} hit-id]
    (let [{:keys [doc-id hit-start hit-end]} (parse-hit-id hit-id)]
      (p/handle-query
       this (bl-server-url server web-service index :resource (str "docs/" doc-id "snippet"))
       (merge {:wordsaroundhit snippet-size
               :hitstart hit-start
               :hitend hit-end
               :jsonp "callback"}
              default-query-params)
       :method jsonp)))
  
  (p/transform-data [corpus data]
    (let [{{:keys [message code] :as error} :error :as cljs-data} (js->clj data :keywordize-keys true)
          uri (bl-server-url server web-service index)]
      (.log js/console "transform this" data)
      (if error
        {:message message :code code}
        (let [{summary :summary hits :hits doc-infos :docInfos} cljs-data
              {{from :first :as params} :searchParam counting? :stillCounting} summary]
          (when counting? (on-counting timeout-ids {:uri uri :params params :callback callback}))
          {:results-summary (normalize-results-summary summary)
           :results (normalize-results doc-infos hits from)
           :status {:status :ok}}))))
 
  (p/transform-error-data [corpus data]
    (identity data))

  (p/get-corpus-info [corpus]
    (let [uri (bl-server-url server web-service index :resource "")]
      (if-let [corpus-info (some-> (with-ls-cache uri) keywordify)]
        (p/handle-info corpus-info)
        (jsonp uri {:params {:jsonp "callbackInfo"} ;avoid overwriting jsonp callback `callback`
                    :json-callback-str "callbackInfo"
                    :handler #(->> % normalize-corpus-info (with-ls-cache uri) p/handle-info)
                    :error-handler #(.log js/console %)})))))

;;; parse cleebo args -> blacklab-server params
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
  (map (fn [{:keys [attribute value]}] (join-params attribute value)) ms))

(defn bl-server-url
  "builds the blacklab server url"
  [server web-service index & {:keys [resource] :or {resource "hits"}}]
  (str "http://" server "/" web-service "/" index "/" resource))

(defn bl-server-sort-str
  "builds the blacklab sort string from param maps"
  [sort-opts filter-opts]
  (apply str (interpose "," (concat (parse-sort-opts sort-opts) (parse-filter-opts filter-opts)))))

;;; handle counting callbacks
(defn clear-timeout [timeout-ids]
  (doseq [timeout-id @timeout-ids]
    (js/clearTimeout timeout-id))
  (reset! timeout-ids []))

(defn timeout-fn [timeout-ids {:keys [uri params callback] :as opts}]
  (fn []
    (jsonp uri
     {:params (assoc params :number 0 :jsonp "callback")
      :error-handler identity
      :handler 
      #(let [{error :error :as data} (js->clj % :keywordize-keys true)]
         (if-not error
           (let [{{query-size :numberOfHits counting? :stillCounting} :summary} data]
             (callback query-size)
             (when counting? (on-counting timeout-ids (update opts :retried-count inc))))
           (timbre/info "Error occurred when requesting counted hits")))})))

(defn on-counting
  [timeout-ids
   {:keys [uri params callback retry-count retried-count]
    :or {retry-count 5 retried-count 0} :as opts}]
  (when (< retried-count retry-count)
    (->> (js/setTimeout
          (timeout-fn timeout-ids opts)
          (+ 1000 (* 1500 retried-count)))
         (swap! timeout-ids conj))))

;;; handle internal sort state (blacklab-server is stateless)
(defn maybe-reset-last-action [last-action {:keys [action-id]}]
  (if-not (= action-id (:action-id @last-action))
    (reset! last-action {})))

(defn get-sort-opts [last-action]
  (let [{:keys [action opts]} @last-action]
    (when (= action :sort)
      opts)))

(defn set-last-action [last-action {:keys [action-id action opts]}]
  (swap! last-action assoc :action action :opts opts :action-id action-id))

;;; normalize blacklab-out -> cleebo app-schemas
(defn sub-hit [{:keys [punct id word]} & {:keys [is-match?]}]
  (mapv (fn [token-word token-id]
          (if is-match?
            {:word token-word :id token-id :match true}
            {:word token-word :id token-id}))
        word
        id))

(defn normalize-meta [num doc]
  (assoc doc :num num))

(defn parse-hit-id
  [hit-id]
  (let [[doc-id hit-start hit-end] (clojure.string/split hit-id #"\.")]
    {:doc-id doc-id :hit-start hit-start :hit-end hit-end}))

(defn normalize-bl-hit
  [hit num doc]
  (let [{left :left match :match right :right doc-id :docPid start :start end :end} hit]    
    {:hit (concat (sub-hit left) (sub-hit match :is-match? true) (sub-hit right))
     :id (apply str (interpose "." [doc-id start end]))
     :meta (normalize-meta num doc)}))

(defn normalize-results-summary
  [{{from :first corpus :indexname query-str :patt num-hits :number} :searchParam
    query-size :numberOfHits query-time :searchTime has-next :windowHasNext}]
  {:page {:from (->int from) :to (+ (->int from) (->int num-hits))}
   :query-size query-size
   :query-str query-str
   :query-time query-time
   :has-next has-next
   :corpus corpus})

(defn normalize-results [doc-infos hits from]
  (vec (map-indexed
        (fn [idx {doc-id :docPid :as hit}]
          (let [num (+ idx (->int from))
                doc (get doc-infos (keyword doc-id))]
            (normalize-bl-hit hit num doc)))
        hits)))

(defn normalize-corpus-info [data]
  (let [{{created :timeCreated last-modified :timeModified} :versionInfo
         {metadata :metadataFields {{props :basicProperties} :contents} :complexFields} :fieldInfo
         corpus-name :indexName word-count :tokenCount status :status}
        (js->clj data :keywordize-keys true)]
    {:corpus-info {:corpus-name corpus-name
                   :word-count word-count
                   :created created
                   :last-modified last-modified}
     :status status
     :sort-props props}))

;; (def mbg-corpus
;;   (make-blacklab-server-corpus
;;    {:index "mbg-index-small"
;;     :server "mbgserver.uantwerpen.be:8080"
;;     :web-service "blacklab-server-1.4-SNAPSHOT"}))


