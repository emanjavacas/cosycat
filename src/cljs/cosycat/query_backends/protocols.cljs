(ns cosycat.query-backends.protocols
  (:require [re-frame.core :as re-frame]
            [cosycat.utils :refer [format keywordify]]
            [cosycat.localstorage :refer [with-ls-cache]]
            [cosycat.ajax-jsonp :refer [jsonp]]
            [ajax.core :refer [GET]]
            [taoensso.timbre :as timbre]))

(defprotocol Corpus
  "abstraction over different corpus query engine services"
  (query [corpus query-str query-opts]
    "run a query defined by `query-str` and retrieves `page-size` hits
     starting `from` with `context` `query-opts` {:page-size int :from int :context int}")  
  (query-sort [corpus query-str query-opts sort-opts filter-opts]
    "run a query defined by `query-str` applying query opts `query-opts`:
    `sort-opts`   [{:position \"(match|left|right)\" :attribute attribute :facet facet}]
    `filter-opts` [{:attribute \"(textType|genre)\" :value \"(sermon|history)\"}]
     opts: `attribute` (word|pos|lemma); `facet` (sensitive|insensitive)")
  (transform-query-data [corpus data opts]
    "transforms success query payload to app internal query data structures")
  (transform-query-error-data [corpus data]
    "transforms error query payload to app internal query error data structures")
  (snippet [corpus query-str query-opts hit-id dir]
    "fetches text surrounding a given query hit identified by `hit-id` in a 
     given `dir` (:left :right nil)")
  (transform-snippet-data [corpus data opts])
  (transform-snippet-error-data [corpus data])
  (query-hit [corpus hit-id opts handler error-handler]
    "retrieve a hit identified by `hit-id`. This method takes a callback `handler` which
     has to be called by new query backends implementation with the result of normalizing 
     the hit to cosycat hit internal representation, and a callback `error-handler` which
     is called with error payload.
     `opts` {:words-left int :words-right int}")
  (transform-query-hit-data [corpus data opts])
  (transform-query-hit-error-data [corpus data])
  (corpus-info [corpus]
    "retrieves corpus info with the following form:
      {:corpus-info {:corpus-name string
                     :word-count int
                     :created string
                     :last-modified string}
       :status (one-of :available :unavailable)
       :sort-props {:sort-opts {key {:facet bool}}
                    :filter-opts [string]}}
     Given that corpus resources are static, this method can easily be cached.")
  (transform-corpus-info-data [corpus data opts])
  (transform-corpus-info-error-data [corpus data]))

(defprotocol JsonpCorpus
  "A protocol to be implemented by search engines that are deployed using jsonp, in which case
   use errors are expected through the success handler (to be able to distinguish from mere
   network timeout errors."
  (is-error? [corpus data]
    "returns a boolean identifying the server payload as error or not")
  (callback-id [corpus]
    "returns a unique callback id for each new call. It is used to handle registering jsonp
     unique callbacks. Especially for calls to `query-hit`"))

(defn method-dispatcher [method]
  (cond (= method GET)   :get
        (= method jsonp) :jsonp
        :default         :get))

(defn debug [{:keys [prefix] :or {prefix ""}} stuff]
  (timbre/debug "STUFF:" prefix stuff)
  stuff)

(defn safe-error-handler [data]
  (re-frame/dispatch
   [:notify
    {:message "A network timeout error occurred. This can have various causes. Contact admin!"
     :code (str "Unrecognized error" ": " data)}]))

;;; Queries
(defn- query-error-handler
  [{:keys [message code]}]
  (re-frame/dispatch [:query-error {:message message :code code :status :error}]))

(defn- query-handler
  [{results :results results-summary :results-summary :as payload}]
  (re-frame/dispatch [:set-query-results payload]))

(defn- safe-query-error-handler [data]
  (re-frame/dispatch
   [:query-error
    {:message "A network timeout error occurred. This can have various causes. Contact admin!"
     :code (str "Unrecognized query error" ": " data)}]))

(defmulti handle-query
  "Helper fn for ajax/jsonp queries that simplifies protocol implementations.
   This function should be called by the `query` method of new query backends."
  (fn [{:keys [method]}] (method-dispatcher method)))

(defmethod handle-query :get
  [{:keys [corpus url params opts]}]
  (GET url
       {:params params
        :handler (fn [data]
                   (let [cljs-data (js->clj data :keywordize-keys true)]
                     (-> (transform-query-data corpus cljs-data opts) query-handler)))
        :error-handler (fn [data]
                         (->> (js->clj data :keywordize-keys true)
                              (transform-query-error-data corpus)
                              query-error-handler))}))

(defmethod handle-query :jsonp
  [{:keys [corpus url params opts]}]
  (jsonp url
         {:params params
          :handler
          (fn [data]
            (let [cljs-data (js->clj data :keywordize-keys true)]
              (if-not (is-error? corpus cljs-data)
                (-> (transform-query-data corpus cljs-data opts) query-handler)
                (-> (transform-query-error-data corpus cljs-data) query-error-handler))))
          :error-handler safe-query-error-handler}))

;;; Snippets
(defn snippet-handler [snippet-data]
  (re-frame/dispatch [:open-modal :snippet snippet-data]))

(defn snippet-error-handler
  [{:keys [code message]}]
  (re-frame/dispatch [:notify {:message message :code code}]))

(defmulti handle-snippet
  "Corpus snippet handler. This function should be used by the `snippet` method
  of new query backends."
  (fn [{:keys [corpus url params method]}] (method-dispatcher method)))

(defmethod handle-snippet :get
  [{:keys [corpus url params opts]}]
  (GET url
       {:params params
        :handler (fn [data] (let [cljs-data (js->clj data :keywordize-keys true)]
                              (-> (transform-snippet-data corpus cljs-data opts) snippet-handler)))
        :error-handler (fn [data] (->> (js->clj data :keywordize-keys true)
                                       (transform-snippet-error-data corpus)
                                       snippet-error-handler))}))

(defmethod handle-snippet :jsonp
  [{:keys [corpus url params opts]}]
  (let [json-callback-str "callbackSnippet"]
    (jsonp url
           {:params (assoc params :jsonp json-callback-str)
            :handler
            (fn [data]
              (let [cljs-data (js->clj data :keywordize-keys true)]
                (if-not (is-error? corpus cljs-data)
                  (-> (transform-snippet-data corpus cljs-data opts) snippet-handler)
                  (-> (transform-snippet-error-data corpus cljs-data) snippet-error-handler))))
            :error-handler safe-error-handler
            :json-callback-str json-callback-str})))

;; Query hit
(defmulti handle-query-hit
  "Corpus query-hit handler. This function should be used by the `query-hit` method
   of new query backends."
  (fn [{:keys [method]}] (method-dispatcher method)))

(defmethod handle-query-hit :get
  [{:keys [corpus url params opts handler error-handler]}]
  (GET url
       {:params params
        :handler (fn [data]
                   (let [cljs-data (js->clj data :keywordize-keys true)]
                     (-> (transform-query-hit-data corpus cljs-data opts) handler)))
        :error-handler (fn [data]
                         (let [cljs-data (js->clj data :keywordize-keys true)]
                           (-> (transform-query-hit-error-data corpus cljs-data) error-handler)))}))

(defmethod handle-query-hit :jsonp
  [{:keys [corpus url params opts handler error-handler]}]
  (jsonp url
         (let [jsonp-callback-str (str "callback" (callback-id corpus))]
           {:params (assoc params :jsonp jsonp-callback-str)
            :handler (fn [data]
                       (let [cljs-data (js->clj data :keywordize-keys true)]
                         (println cljs-data)
                         (if-not (is-error? corpus cljs-data)
                           (-> (transform-query-hit-data corpus cljs-data opts) handler)
                           (-> (transform-query-hit-error-data corpus cljs-data) error-handler))))
            :error-handler safe-error-handler
            :json-callback-str jsonp-callback-str})))

;;; Corpus info
(defn- corpus-info-handler
  [{{corpus-name :corpus-name} :corpus-info :as corpus-info}]
  (re-frame/dispatch [:set-corpus-info corpus-name corpus-info]))

(defn- corpus-info-error-handler
  [{:keys [message code]}]
  (re-frame/dispatch
   [:notify {:message (format "Error while retrieve corpus info: " message)
             :code code
             :status :error}]))

(defmulti handle-corpus-info
  "Corpus info handler. This function should be used by the `corpus-info` method
   of new query backends."
  (fn [{:keys [method]}] (method-dispatcher method)))

(defmethod handle-corpus-info :get
  [{:keys [corpus url params opts]}]
  (if-let [corpus-info (some-> (with-ls-cache url) keywordify)]
    (corpus-info-handler corpus-info)
    (GET url
     {:params params
      :handler (fn [data]
                 (let [cljs-data (js->clj data :keywordize-keys true)]
                   (->> (transform-corpus-info-data corpus cljs-data opts)
                        (with-ls-cache url)
                        corpus-info-handler)))
      :error-handler
      (fn [data] (->> (js->clj data :keywordize-keys true)
                      (transform-corpus-info-error-data corpus)
                      corpus-info-error-handler))})))

(defmethod handle-corpus-info :jsonp
  [{:keys [corpus url params opts]}]
  (if-let [corpus-info (some-> (with-ls-cache url) keywordify)]
    (corpus-info-handler corpus-info)
    (jsonp url
     {:params (assoc params :jsonp "callbackInfo") ;avoid overwriting jsonp callback `callback`
      :json-callback-str "callbackInfo"
      :handler (fn [data]
                 (let [cljs-data (js->clj data :keywordize-keys true)]
                   (if-not (is-error? corpus cljs-data)
                     (->> (transform-corpus-info-data corpus cljs-data opts)
                          (with-ls-cache url)
                          corpus-info-handler)
                     (->> cljs-data
                          (transform-corpus-info-error-data corpus)
                          corpus-info-error-handler))))
      :error-handler safe-error-handler})))

(def corpus
  {:corpus "mbg-index"
   :type :blacklab-server
   :args {:server "mbgserver.uantwerpen.be:8080"
          :web-service "blacklab-server-1.6.0-SNAPSHOT"}})
