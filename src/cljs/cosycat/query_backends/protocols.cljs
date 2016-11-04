(ns cosycat.query-backends.protocols
  (:require [re-frame.core :as re-frame]
            [cosycat.utils :refer [format]]
            [ajax.core :refer [GET]]
            [cosycat.ajax-jsonp :refer [jsonp]]
            [taoensso.timbre :as timbre]))

(defprotocol Corpus
  "abstraction over different corpus query engine services"
  (query [corpus query-str query-opts]
    "retrieve `query-str` and retrieves `page-size` hits starting `from` with `context`
    `query-opts` {:page-size int :from int :context int}")  
  (query-sort [corpus query-str query-opts sort-opts filter-opts]
    "retrieve a query on `query-str` & query opts `query-opts`:
    `sort-opts`   [{:position \"(match|left|right)\" :attribute attribute :facet facet}]
    `filter-opts` [{:attribute \"(textType|genre)\" :value \"(sermon|history)\"}]
     opts: `attribute` (word|pos|lemma); `facet` (sensitive|insensitive)")
  (query-hit [corpus hit-id opts handler]
    "retrieve a hit identified by `hit-id`. This method takes a callback `handler` which
     has to be called by new query backends implementation with the result of normalizing 
     the hit to cosycat hit internal representation.
     `opts` {:words-left int :words-right int}")
  (snippet [corpus query-str query-opts hit-id dir]
    "fetches text surrounding a given query hit identified by `hit-id` in a 
     given `dir` (:left :right nil)")
  (transform-data [corpus data]
    "transforms success payload to app internal data structure")
  (transform-error-data [corpus data]
    "transforms error payload to app internal data structure")
  (corpus-info [corpus]
    "retrieves corpus info with the following form:
      {:corpus-info {:corpus-name string
                     :word-count int
                     :created string
                     :last-modified string}
       :status (one-of :available :unavailable)
       :sort-props {:sort-opts {key {:facet bool}}
                    :filter-opts [string]}}
     Given that corpus resources are static, this method can easily be cached."))

(defn method-dispatcher [method]
  (cond (= method GET)   :get
        (= method jsonp) :jsonp
        :default         :get))

;;; TODO: add schemas

;;; Query handling
(defmulti query-error-handler
  "general error handler called with normalized query error data"
  (fn [data & [method]] (method-dispatcher method)))

(defmethod query-error-handler :get
  [{:keys [message code]}               ;TODO :- query-error-schema
   & [method]]
  (re-frame/dispatch [:query-error {:message message :code code}]))

(defmethod query-error-handler :jsonp
  [data & [method]]
  (re-frame/dispatch
   [:query-error
    {:message "A network timeout error occurred. This can have various causes. Contact admin!"
     :code (str "Unrecognized query error" ": " data)}]))

(defmulti query-handler
  "general handler called with normalized corpus query data"
  (fn [data & [method]] (method-dispatcher method))
  :default :get)

(defmethod query-handler :get
  [{results :results results-summary :results-summary :as payload} ;TODO :- query-schema
   & [method]]
  (re-frame/dispatch [:set-query-results payload]))

(defmethod query-handler :jsonp
  [{results :results results-summary :results-summary ;success data
    message :message code :code         ;error data
    :as payload}                        ;TODO :- query-schema-jsonp
   & [method]]
  (if (or message code)                 ;query has error
    (query-error-handler payload)       ;get-default error handler
    (re-frame/dispatch [:set-query-results (dissoc payload :message :code)])))

(defn handle-query
  "Helper fn for ajax/jsonp queries that simplifies protocol implementations.
   This function should be called by the `query` method of new query backends."
  [corpus url params & {:keys [method] :or {method GET}}]
  (method url
   {:params params
    :handler (fn [data] (query-handler (transform-data corpus data) method))
    :error-handler (fn [data] (query-error-handler (transform-error-data corpus data) method))}))

;;; Corpus info
(defn corpus-info-handler
  "Corpus info handler.
   This function should be used by the `corpus-info` method of new query backends."
  [{{corpus-name :corpus-name} :corpus-info :as corpus-info}] ;TODO :- corpus-info-schema
  (re-frame/dispatch [:set-corpus-info corpus-name corpus-info]))

;;; Snippets
(defmulti snippet-error-handler
  "Snippet error handler.
   This function should be used by the `snippet` method of new query backends."
  (fn [data & [method]] (method-dispatcher method))
  :default :get)

(defmethod snippet-error-handler :get
  [{:keys [code message]}               ;TODO :- snippet-error-schema
   & [method]]
  (re-frame/dispatch [:notify {:message message :code code}]))

(defmethod snippet-error-handler :jsonp
  [{{:keys [code message]} :error} & [method]]
  (re-frame/dispatch
   [:notify
    {:message (format "Unrecognized error [%s] while retrieving snippet: %s" code message)
     :status :error}]))

(defmulti snippet-handler
  "Snippet success handler.
   This function should be used by the `snippet` method of new query backends."
  (fn [data & [method]] (method-dispatcher method))
  :default :get)

(defmethod snippet-handler :get
  [data                                 ;TODO :- snippet-schema
   & [method]]
  (re-frame/dispatch [:open-modal :snippet data]))

(defmethod snippet-handler :jsonp
  [{{:keys [message code] :as error} :error :as data} ;TODO :- snippet-schema-jsonp
   & [method]]
  (if error
    (snippet-error-handler data)        ;get default error-handler
    (re-frame/dispatch [:open-modal :snippet data])))


(def corpus
  {:corpus "mbg-index"
   :type :blacklab-server
   :args {:server "mbgserver.uantwerpen.be:8080"
          :web-service "blacklab-server-1.6.0-SNAPSHOT"}})

;; (let [c (cosycat.query-backends.core/ensure-corpus corpus)]
;;   (query-hit c "10152315.103.104" {:words-left 11 :words-right 10} #(.log js/console %)))


