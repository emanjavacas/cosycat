(ns cleebo.query-backends.protocols
  (:require [re-frame.core :as re-frame]
            [cleebo.utils :refer [format]]
            [ajax.core :refer [GET]]
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
     opts: attribute (word|pos|lemma); facet (sensitive|insensitive)")
  (snippet [corpus query-str query-opts hit-id dir]
    "fetches text surrounding a given query hit identified by `hit-id` in a 
     given `dir` (:left :right nil)")
  (transform-data [corpus data]
    "transforms success payload to app internal data structure")
  (transform-error-data [corpus data]
    "transforms error payload to app internal data structure")
  (get-corpus-info [corpus]
    "retrieves corpus info with the following form:
      {:corpus-info {:corpus-name string
                     :word-count int
                     :created string
                     :last-modified string}
       :status (one-of :available :unavailable)
       :sort-props {:sort-opts {key {:facet bool}}
                    :filter-opts [string]}}
     given that corpus resources are static, this method can easily be cached"))

(defn handler
  "general handler called with normalized corpus query data"
  [{results :results results-summary :results-summary ;success data
    message :message code :code         ;error data
    :as payload}]
  (if (or message code)
    (re-frame/dispatch [:query-error {:message message :code code}])
    (re-frame/dispatch [:set-query-results (dissoc payload :message :code)])))

(defn error-handler
  "general error handler called with normalized query error data"
  [data]
  (re-frame/dispatch
   [:query-error
    {:message "A network timeout error occurred. This can have various causes. Contact admin!"
     :code (str "Unrecognized query error" ": " data)}]))

(defn handle-info
  [{{corpus-name :corpus-name} :corpus-info :as corpus-info}]
  (re-frame/dispatch [:set-corpus-info corpus-name corpus-info]))

(defn snippet-result-handler [& [context]]
  (fn [{:keys [snippet status hit-idx] :as payload}]
    (let [payload (case context
                    nil payload
                    :left (update-in payload [:snippet] dissoc :right)
                    :right (update-in payload [:snippet] dissoc :left))]
      (re-frame/dispatch [:open-modal :snippet payload]))))

(defn handle-snippet [{{:keys [message code] :as error} :error :as data}]
  (if error
    (re-frame/dispatch
     [:notify
      {:message (format "Error [%s] while retrieving snippet: %s" code message)
       :status :error}])
    (re-frame/dispatch [:open-modal :snippet data])))

(defn handle-query
  "wrapper for ajax/jsonp queries that simplifies protocol implementations"
  [corpus url params & {:keys [method] :or {method GET}}]
  (method url {:params params
               :handler (fn [data] (handler (transform-data corpus data)))
               :error-handler (fn [data] (error-handler (transform-error-data corpus data)))}))

