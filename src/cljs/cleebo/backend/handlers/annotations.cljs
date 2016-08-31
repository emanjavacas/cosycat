(ns cleebo.backend.handlers.annotations
  (:require [re-frame.core :as re-frame]
            [schema.core :as s]
            [ajax.core :refer [POST GET]]
            [cleebo.schemas.annotation-schemas :refer [annotation-schema]]
            [cleebo.app-utils :refer [deep-merge]]
            [cleebo.utils :refer [->int format get-msg get-token-id]]
            [cleebo.backend.middleware :refer [standard-middleware no-debug-middleware]]
            [taoensso.timbre :as timbre]))

;;; Incoming annotations
(defn update-hit [hit anns]
  (mapv (fn [{token-id :id :as token}]
          (if-let [ann (get anns (->int token-id))]
            (update token :anns deep-merge ann)
            token))
        hit))

(defn find-hit-id
  "find the hid id for a continuous span given `token-ids`"
  [token-ids hit-maps]
  (some (fn [{:keys [id hit]}]
          (let [{from :id} (first hit)
                {to :id} (last hit)]
            (when (some #(and (>= % from) (<= % to)) token-ids)
              id)))
        hit-maps))

(defmulti add-annotations
  "generic reducer function for incoming annotation data"
  (fn [db map-or-maps] (type map-or-maps)))

(defmethod add-annotations cljs.core/PersistentArrayMap
  [db {project :project hit-id :hit-id anns :anns}]
  (let [results-by-id (get-in db [:projects project :session :query :results-by-id])
        path [:projects project :session :query :results-by-id hit-id :hit]]
    (if (contains? results-by-id hit-id)
      (do (timbre/debug "contains") (update-in db path update-hit anns))
      (if-let [hit-id (find-hit-id (keys anns) (vals results-by-id))]
        (do (timbre/debug "found") (update-in db path update-hit anns))
        (do (timbre/debug "didn't found") db)))))

(defmethod add-annotations cljs.core/PersistentVector
  [db ms]
  (reduce (fn [db m] (add-annotations db m)) db ms))

(re-frame/register-handler              ;generic handler
 :add-annotation
 standard-middleware
 (fn [db [_ map-or-maps]] (add-annotations db map-or-maps)))

(re-frame/register-handler
 :fetch-annotations
 standard-middleware
 (fn [db [_ {:keys [page-margins]}]]
   (let [project (get-in db [:session :active-project])
         corpus (get-in db [:projects project :session :query :results-summary :corpus])]
     (re-frame/dispatch [:start-throbbing :fetch-annotations])
     (GET "/annotation/page"
          {:params {:page-margins page-margins :project project :corpus corpus}
           :handler #(do (re-frame/dispatch [:add-annotation %])
                         (re-frame/dispatch [:stop-throbbing :fetch-annotations]))
           :error-handler #(do (re-frame/dispatch [:stop-throbbing :fetch-annotations])
                               (timbre/info "Couldn't fetch anns" %))}))
   db))

;;; Outgoing annotations
(s/defn ^:always-validate make-annotation :- annotation-schema
  ([ann-map hit-id token-id :- s/Int]
   (merge ann-map {:hit-id hit-id
                   :span {:type "token" :scope token-id}
                   :timestamp (.now js/Date)}))
  ([ann-map hit-id token-from :- s/Int token-to :- s/Int]
   {:pre [(>= token-to token-from)]}
   (merge ann-map {:hit-id hit-id
                   :span {:type "IOB" :scope {:B token-from :O token-to}}
                   :timestamp (.now js/Date)})))

(defmulti dispatch-annotation-handler
  "Variadic handler for successful annotations. Dispatches are based on whether
  ann-map is a vector (bulk annotation payload) or a map (single annotation payload)"
  type)

(defn notification-message
  [{{{B :B O :O :as scope} :scope type :type} :span :as data} message]
  (->> (case type
         "token" (get-msg [:annotation :error :token] scope message)
         "IOB" (get-msg [:annotation :error :IOB] B O message))
       (assoc {} :message)))

(defn dispatch-annotation-history [data] ;data should be anns
  (re-frame/dispatch [:register-history [:server-events] {:type :annotation :payload data}]))

(defmethod dispatch-annotation-handler cljs.core/PersistentArrayMap
  [{status :status
    message :message                    ;error message in case of error
    {project :project hit-id :hit-id anns :anns ;success payload
     {{B :B O :O :as scope} :scope type :type} :span ;error payload
     :as data} :data}]
  (case status
    :ok (do (re-frame/dispatch [:add-annotation data])
            ;; add update
            (dispatch-annotation-history data)
            (re-frame/dispatch [:notify {:message (str "Added 1 annotation")}]))
    :error (re-frame/dispatch [:notify (notification-message data message)])))

(defmethod dispatch-annotation-handler cljs.core/PersistentVector
  [ms]
  (let [{oks :ok errors :error :as grouped} (group-by :status ms)
        message (str "Added " (count oks) " annotations with " (count errors) " errors")]
    (when-not (empty? oks)
      (do (re-frame/dispatch [:add-annotation (mapv :data oks)])
          ;; add update
          (dispatch-annotation-history (mapv :data oks))
          (re-frame/dispatch [:notify {:message message}])))
    (doseq [{data :data message :message} errors]
      (re-frame/dispatch [:notify (notification-message data message)]))))

(defn error-handler [& args]
  (re-frame/dispatch [:notify {:message "Unrecognized internal error"}]))

(declare package-annotation)

(re-frame/register-handler
 :dispatch-annotation
 (fn [db [_ ann & args]]
   (let [project (get-in db [:session :active-project])
         corpus (get-in db [:projects project :session :query :results-summary :corpus])
         query (get-in db [:projects project :session :query :results-summary :query-str])         
         ann-map {:ann ann :corpus corpus :query query}]
     (try (POST "/annotation/new"
                {:params (apply package-annotation ann-map project args)
                 :handler dispatch-annotation-handler
                 :error-handler error-handler})
          (catch :default e
            (re-frame/dispatch
             [:notify {:message (format "Couldn't dispatch annotation: %s" (str e))}])))
     db)))

(defn update-annotation-handler
  [{status :status message :message data :data}]
  (condp = status
    :ok (do (re-frame/dispatch [:add-annotation data])
            (dispatch-annotation-history data))
    :error (re-frame/dispatch
            [:notify
             {:message (format "Couldn't update annotation! Reason: [%s]" message)
              :meta data}])))

(re-frame/register-handler
 :update-annotation
 (fn [db [_ {{:keys [_version _id hit-id value] :as update-map} :update-map}]]
   (let [project (get-in db [:session :active-project])
         corpus (get-in db [:projects project :session :query :results-summary :corpus])
         query (get-in db [:projects project :session :query :results-summary :query-str])
         update-map (assoc update-map :timestamp (.now js/Date) :corpus corpus :query query)]
     (POST "/annotation/update"
           {:params {:update-map update-map :project project}
            :handler update-annotation-handler
            :error-handler error-handler})
     db)))

;;; Utils
(defmulti package-annotation
  "packages annotation data for the server. It only supports bulk payloads for token annotations"
  (fn [ann-map-or-maps project hit-id token-id & [token-to]]
    [(type ann-map-or-maps) (type token-id)]))

(s/defmethod package-annotation
  [cljs.core/PersistentArrayMap js/Number]
  ([ann-map project hit-id token-id :- s/Int]
   (let [ann-map (make-annotation ann-map hit-id token-id)]
     {:project project
      :ann-map ann-map}))
  ([ann-map project hit-id token-from :- s/Int token-to :- s/Int]
   (let [ann-map (make-annotation ann-map hit-id token-from token-to)]
     {:project project
      :ann-map ann-map})))

(s/defmethod package-annotation
  [cljs.core/PersistentArrayMap cljs.core/PersistentVector]
  [ann-map project hit-ids :- [s/Any] token-ids :- [s/Int]]
  (let [ann-maps (mapv (fn [token-id hit-id] (make-annotation ann-map hit-id token-id))
                       token-ids hit-ids)]
    {:project project
     :ann-map ann-maps}))

(s/defmethod package-annotation
  [cljs.core/PersistentVector cljs.core/PersistentVector]
  [anns project hit-ids :- [s/Any] token-ids :- [s/Int]]
  {:pre [(apply = (map count [anns hit-ids]))]}
  (let [ann-maps (mapv (fn [ann hit-id token-id] (make-annotation ann hit-id token-id))
                       anns hit-ids token-ids)]
    {:project project
     :ann-map ann-maps}))
