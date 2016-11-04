(ns cosycat.backend.handlers.annotations
  (:require [re-frame.core :as re-frame]
            [schema.core :as s]
            [ajax.core :refer [POST GET]]
            [cosycat.schemas.annotation-schemas :refer [annotation-schema]]
            [cosycat.app-utils :refer [deep-merge is-last-partition token-id->span span->token-id]]
            [cosycat.utils :refer [format get-msg now]]
            [cosycat.backend.middleware :refer [standard-middleware]]
            [taoensso.timbre :as timbre]))

;;; Incoming annotations
(defn update-hit [hit anns]
  (mapv (fn [{token-id :id :as token}]
          (if-let [ann (get anns token-id)]
            (update token :anns deep-merge ann)
            token))
        hit))

(defn is-token? [id token-id-or-ids]
  (if (sequential? token-id-or-ids)
    (contains? (apply hash-set token-id-or-ids) id)
    (= id token-id-or-ids)))

(defn delete-ann [hit token-id-or-ids key]
  (mapv (fn [{id :id anns :anns :as token}]
          (if (and (is-token? id token-id-or-ids) (contains? anns key))
            (assoc token :anns (dissoc anns key))
            token))
        hit))

(defn find-hit-id
  "find the hid id given `token-ids`"
  [token-id-or-ids hit-maps]
  (if (coll? token-id-or-ids)
    (some #(find-hit-id % hit-maps) token-id-or-ids)
    (some (fn [{:keys [id hit]}]
            (when (some #{token-id-or-ids} (map :id hit))
              id))
          hit-maps)))

(defmulti add-annotations
  "generic reducer function for incoming annotation data"
  (fn [db map-or-maps] (type map-or-maps)))

(defmethod add-annotations cljs.core/PersistentArrayMap
  [db {project :project hit-id :hit-id anns :anns}]
  (let [results-by-id (get-in db [:projects project :session :query :results-by-id])
        path [:projects project :session :query :results-by-id hit-id :hit]]
    (if (contains? results-by-id hit-id)
      (update-in db path update-hit anns) ;found hit by id
      (if-let [hit-id (find-hit-id (keys anns) (vals results-by-id))]
        (update-in db path update-hit anns) ;found hit for annotation
        db)))) ;couldn't find hit for annotation

(defmethod add-annotations cljs.core/PersistentVector
  [db ms]
  (reduce (fn [db m] (add-annotations db m)) db ms))

(re-frame/register-handler              ;generic handler
 :add-annotation
 standard-middleware
 (fn [db [_ map-or-maps]] (add-annotations db map-or-maps)))

(re-frame/register-handler
 :remove-annotation
 standard-middleware
 (fn [db [_ project hit-id key {type :type :as span}]]
   (let [path [:projects project :session :query :results-by-id hit-id :hit]
         results (vals (get-in db [:projects project :session :query :results-by-id]))
         token-id-or-ids (span->token-id span)]
     (if-let [hit (get-in db path)]
       (update-in db path delete-ann token-id-or-ids key) ;found hit by id
       (if-let [hit-id (find-hit-id token-id-or-ids results)]
         (update-in db path delete-ann token-id-or-ids key) ;found hit for annotation
         db))))) ;couldn't find hit

(defn fetch-annotation-handler [& {:keys [is-last]}]
  (fn [ann]
    (re-frame/dispatch [:add-annotation ann])
    (when is-last
      (re-frame/dispatch [:stop-throbbing :fetch-annotations]))))

(defn fetch-annotation-error-handler []
  (fn [data]
    (re-frame/dispatch [:stop-throbbing :fetch-annotations])
    (timbre/info "Couldn't fetch anns" data)))

(re-frame/register-handler
 :fetch-annotations
 standard-middleware
 (fn [db [_ {:keys [page-margins]}]]    ;[{:start token-id :end token-id :hit-id .. :doc ..}]
   (let [project (get-in db [:session :active-project])
         corpus (get-in db [:projects project :session :query :results-summary :corpus])
         margins (count page-margins)
         partition-size 20]
     (re-frame/dispatch [:start-throbbing :fetch-annotations])
     (doseq [[i subpage-margins] (map-indexed vector (partition-all partition-size page-margins))
             :let [is-last (is-last-partition margins partition-size i)]]
       (GET "/annotation/page"
            {:params {:page-margins subpage-margins :project project :corpus corpus}
             :handler (fetch-annotation-handler :is-last is-last)
             :error-handler (fetch-annotation-error-handler)})))
   db))

;;; Outgoing annotations
(s/defn make-annotation :- annotation-schema
  ([ann-map hit-id token-id]
   (merge ann-map {:hit-id hit-id :span (token-id->span token-id) :timestamp (now)}))
  ([ann-map hit-id token-from token-to]
   (merge ann-map {:hit-id hit-id :span (token-id->span token-from token-to) :timestamp (now)})))

(defmulti dispatch-annotation-handler
  "Variadic handler for successful annotations. Dispatches are based on whether
  ann-map is a vector (bulk annotation payload) or a map (single annotation payload)"
  type)

(defn notification-message
  [{{{B :B O :O :as scope} :scope type :type} :span} message]
  (->> (case type
         "token" (get-msg [:annotation :error :token] scope message)
         "IOB" (get-msg [:annotation :error :IOB] B O message))
       (assoc {} :message)))

(defmethod dispatch-annotation-handler cljs.core/PersistentArrayMap
  [{status :status message :message data :data}]
  (case status
    :ok (do (re-frame/dispatch [:add-annotation data])
            (re-frame/dispatch [:notify {:message (str "Added 1 annotation")}]))
    :error (re-frame/dispatch [:notify (notification-message data message)])))

(defmethod dispatch-annotation-handler cljs.core/PersistentVector
  [ms]
  (let [{oks :ok errors :error :as grouped} (group-by :status ms)
        message (str "Added " (count oks) " annotations with " (count errors) " errors")]
    (when-not (empty? oks)
      (do (re-frame/dispatch [:add-annotation (mapv :data oks)])
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
             [:notify {:message (format "Couldn't dispatch annotation. Reason: [%s]" (str e))}])))
     db)))

(defn update-annotation-handler
  [{status :status message :message data :data}]
  (condp = status
    :ok (re-frame/dispatch [:add-annotation data])
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

(defn remove-annotation-handler
  [{{project :project hit-id :hit-id span :span key :key :as data} :data
    status :status message :message}]
  (condp = status
    :ok (re-frame/dispatch [:remove-annotation project hit-id key span])
    :error (re-frame/dispatch
            [:notify {:message (format "Couldn't remove annotation! Reason: [%s]" message)
                      :meta data}])))

(re-frame/register-handler
 :delete-annotation
 (fn [db [_ {:keys [ann-map hit-id]}]]
   (let [project (get-in db [:session :active-project])
         corpus (get-in db [:projects project :session :query :results-summary :corpus])]
     (POST "/annotation/remove"
           {:params {:project project :hit-id hit-id :ann ann-map}
            :handler remove-annotation-handler
            :error-handler error-handler})
     db)))

;;; Utils
(defmulti package-annotation
  "packages annotation data for the server. It only supports bulk payloads for token annotations"
  (fn [ann-map-or-maps project hit-id token-id & [token-to]]
    [(type ann-map-or-maps) (coll? token-id)]))

(defmethod package-annotation           ;simple token annotation
  [cljs.core/PersistentArrayMap false]
  ([ann-map project hit-id token-id]
   {:project project :ann-map (make-annotation ann-map hit-id token-id)})
  ([ann-map project hit-id token-from token-to]
   {:project project :ann-map (make-annotation ann-map hit-id token-from token-to)}))

(defmethod package-annotation           ;bulk token annotation
  [cljs.core/PersistentArrayMap true]
  [ann-map project hit-ids token-ids]
  (->> (mapv (fn [token-id hit-id] (make-annotation ann-map hit-id token-id)) token-ids hit-ids)
       (assoc {:project project} :ann-map)))

(defmethod package-annotation
  [cljs.core/PersistentVector true]
  [anns project hit-ids token-ids]
  (assert (apply = (map count [anns hit-ids])) "Each ann must have a hit-id")
  (->> (mapv (fn [ann hit-id token-id] (make-annotation ann hit-id token-id)) anns hit-ids token-ids)
       (assoc {:project project} :ann-map)))
