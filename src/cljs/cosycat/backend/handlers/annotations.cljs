(ns cosycat.backend.handlers.annotations
  (:require [re-frame.core :as re-frame]
            [schema.core :as s]
            [ajax.core :refer [POST GET]]
            [cosycat.schemas.annotation-schemas :refer [annotation-schema]]
            [cosycat.app-utils
             :refer [deep-merge is-last-partition token-id->span span->token-id]]
            [cosycat.utils :refer [format get-msg now]]
            [cosycat.backend.middleware :refer [standard-middleware]]
            [cosycat.backend.handlers.utils :refer [get-query get-corpus-param expand-db-path]]
            [taoensso.timbre :as timbre]))

;;; Incoming annotations; query-panel
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

;;; TODO: check multiple db paths
(defmulti add-annotations
  "generic reducer function for incoming annotation data"
  (fn [db {:keys [payload]}] (type payload)))

(defmethod add-annotations cljs.core/PersistentArrayMap
  [db {{:keys [project-name hit-id anns]} :payload db-path :db-path}]
  (let [path-to-results (into [:projects project-name] (expand-db-path db-path))
        path-to-hit (fn [hit-id] (into path-to-results [hit-id :hit]))
        results-by-id (get-in db path-to-results)]
    (if (contains? results-by-id hit-id)
      ;; found hit by id
      (do (timbre/debug "found hit by id") (update-in db (path-to-hit hit-id) update-hit anns))
      (if-let [hit-id (find-hit-id (keys anns) (vals results-by-id))]
        ;; found hit for annotation
        (do (timbre/debug "found hit") (update-in db (path-to-hit hit-id) update-hit anns))
        ;; couldn't find hit for annotation
        (do (timbre/debug "couldn't find hit") db)))))

(defmethod add-annotations cljs.core/PersistentVector
  [db {ms :payload :as data}]
  (reduce (fn [db m] (add-annotations db (assoc data :payload m))) db ms))

(re-frame/register-handler ;; generic handler
 :add-annotation
 standard-middleware
 (fn [db [_ data]] (add-annotations db data)))

(re-frame/register-handler
 :remove-annotation
 standard-middleware
 (fn [db [_ {{project-name :project-name
              hit-id :hit-id
              key :key
              {type :type :as span} :span} :payload
             db-path :db-path}]]
   (let [path-to-results (into [:projects project-name] (expand-db-path db-path))
         results (vals (get-in db path-to-results))
         path-to-hit (fn [hit-id] (into path-to-results [hit-id :hit]))
         token-id-or-ids (span->token-id span)]
     (if-let [hit (get-in db (path-to-hit hit-id))]
       ;; found hit by id
       (update-in db (path-to-hit hit-id) delete-ann token-id-or-ids key)
       (if-let [hit-id (find-hit-id token-id-or-ids results)]
         ;; found hit for annotation
         (update-in db (path-to-hit hit-id) delete-ann token-id-or-ids key)
         ;; couldn't find hit
         db)))))

(defn fetch-annotation-handler [& {:keys [is-last db-path]}]
  (fn [payload]
    (re-frame/dispatch [:add-annotation {:payload payload :db-path db-path}])
    (when is-last
      (re-frame/dispatch [:stop-throbbing :fetch-annotations]))))

(defn fetch-annotation-error-handler []
  (fn [data]
    (re-frame/dispatch [:stop-throbbing :fetch-annotations])
    (timbre/warn "Couldn't fetch anns" data)))

(re-frame/register-handler ;; general annotation fetcher for query hits
 :fetch-annotations
 standard-middleware
 (fn [db [_ {:keys [page-margins corpus db-path] :or {db-path :query}}]]
   (let [project-name (get-in db [:session :active-project])
         margins (count page-margins)
         partition-size 20]
     (re-frame/dispatch [:start-throbbing :fetch-annotations])
     (doseq [[i subpage-margins] (map-indexed vector (partition-all partition-size page-margins))
             :let [is-last (is-last-partition margins partition-size i)]]
       (GET "/annotation/page"
            {:params {:page-margins subpage-margins :project-name project-name :corpus corpus}
             :handler (fetch-annotation-handler :is-last is-last :db-path db-path)
             :error-handler (fetch-annotation-error-handler)})))
   db))

(re-frame/register-handler ;; annotation fetcher for issue hits
 :fetch-issue-hit-annotations
 (fn [db [_ {:keys [start end hit-id doc corpus]} {issue-id :id :as issue}]]
    (let [active-project (get-in db [:session :active-project])]
      (GET "/annotation/page"
           {:params {:page-margins [{:start start :end end :hit-id hit-id :doc doc}]
                     :project-name active-project
                     :corpus corpus}
            :handler (fn [data]
                       (when-let [{:keys [anns]} (first data)]
                         (re-frame/dispatch
                          [:update-issue-meta issue-id [:hit-map :hit]
                           (fn [hit] (update-hit hit anns))])))
            :error-handler #(timbre/warn "Couldn't fetch annotations" (str %))})
      db)))

(re-frame/register-handler ;; annotation fetcher for review pages
 :fetch-review-hit-annotations
 (fn [db [_ {:keys [start end hit-id doc corpus]}]]
    (let [project-name (get-in db [:session :active-project])]
      (GET "/annotation/page"
           {:params {:page-margins [{:start start :end end :hit-id hit-id :doc doc}]
                     :project-name project-name
                     :corpus corpus}
            :handler (fn [data]
                       (when-let [{:keys [hit-id project-name anns]} (first data)]
                         (re-frame/dispatch
                          [:add-review-annotation
                           {:hit-id hit-id :project-name project-name :anns anns}])))
            :error-handler #(timbre/warn "Couldn't fetch annotations" (str %))})
      db)))

(defn query-review-handler [context]
  (fn [{:keys [grouped-data] :as review-summary}]
    {:pre [(apply = (mapcat (fn [{:keys [anns]}] (map :corpus anns)) grouped-data))]}
    (re-frame/dispatch [:set-review-results review-summary context])))

(defn query-review-error-handler [data]
  (timbre/debug data)
  (re-frame/dispatch [:notify {:message "Couldn't fetch annotations"}]))

(defn build-query-map
  [{{ann-key :key ann-value :value} :ann
    {:keys [from to]} :timestamp corpus :corpus username :username :as query-map}]
  (cond-> {}
    (not (empty? ann-key)) (assoc-in [:ann :key] ann-key)
    (not (empty? ann-value)) (assoc-in [:ann :value] ann-value)
    (not (empty? corpus)) (assoc :corpus (vec corpus))
    (not (empty? username)) (assoc :username (vec username))
    from (assoc-in [:timestamp :from] from)
    to (assoc-in [:timestamp :to] to)))

(re-frame/register-handler
 :query-review
 standard-middleware
 (fn [db [_ {:keys [page-num] :or {page-num 1}}]]
   (let [active-project (get-in db [:session :active-project])
         path-to-query-opts [:projects active-project :session :review :query-opts]
         {:keys [query-map context size] :as query-opts} (get-in db path-to-query-opts)]
     (re-frame/dispatch [:unset-review-results])
     (re-frame/dispatch [:start-throbbing :review-frame])
     (GET "annotation/query"
          {:params {:query-map (build-query-map query-map)
                    :context context
                    :page {:page-num page-num :page-size size}
                    :project-name active-project}
           :handler (query-review-handler context)
           :error-handler query-review-error-handler})
     db)))

;;; Outgoing annotations
(defn notification-message
  [{{{B :B O :O :as scope} :scope span-type :type} :span :as data} message]
  (if-not span-type  ;; project-level error (e.g. insufficient rights)
    {:message message}
    (->> (case span-type
           "token" (get-msg [:annotation :error :token] scope message)
           "IOB" (get-msg [:annotation :error :IOB] B O message))
         (assoc {} :message))))

(defmulti dispatch-annotation-handler*
  "Variadic handler for successful annotations. Dispatches are based on whether
  ann-map is a vector (bulk annotation payload) or a map (single annotation payload)"
  (fn [{payload :payload}] (type payload)))

(defmethod dispatch-annotation-handler* cljs.core/PersistentArrayMap
  [{{status :status message :message data :data} :payload db-path :db-path}]
  (case status
    :ok (do (re-frame/dispatch [:add-annotation {:payload data :db-path db-path}])
            (re-frame/dispatch [:notify {:message (str "Added 1 annotation")}]))
    :error (re-frame/dispatch [:notify (notification-message data message)])))

(defmethod dispatch-annotation-handler* cljs.core/PersistentVector
  [{ms :payload db-path :db-path}]
  (let [{oks :ok errors :error :as grouped} (group-by :status ms)
        message (str "Added " (count oks) " annotations with " (count errors) " errors")]
    (when-not (empty? oks)
      (do (re-frame/dispatch [:add-annotation {:payload (mapv :data oks) :db-path db-path}])
          (re-frame/dispatch [:notify {:message message}])))
    (doseq [{data :data message :message} errors]
      (re-frame/dispatch [:notify (notification-message data message)]))))

(defn dispatch-annotation-handler [& {:keys [db-path]}]
  (fn [data] (dispatch-annotation-handler* {:payload data :db-path db-path})))

(defn error-handler [& args]
  (re-frame/dispatch [:notify {:message "Unrecognized internal error"}]))

(re-frame/register-handler
 :dispatch-simple-annotation
 standard-middleware
 (fn [db [_ {:keys [ann-data db-path corpus] :or {db-path :query}}]]
   (let [{:keys [ann-map hit-id token-from token-to]} ann-data
         span (if token-to (token-id->span token-from token-to) (token-id->span token-from))
         ann-map (assoc ann-map :hit-id hit-id :span span :timestamp (now))]
     (re-frame/dispatch [:dispatch-annotation ann-map corpus :db-path db-path]))
   db))

(defn package-ann-maps [ann-map hit-ids token-ids & [token-to's]]
  {:pre [(or (not token-to's) (= (count token-ids) (count token-to's)))]}
  (let [timestamp (now)]
    (if token-to's
      (mapv (fn [hit-id token-from token-to]
              (let [span (token-id->span token-from token-to)]
                (assoc ann-map :hit-id hit-id :span span :timestamp timestamp)))
            hit-ids token-ids token-to's)
      (mapv (fn [hit-id token-id]
              (let [span (token-id->span token-id)]
                (assoc ann-map :hit-id hit-id :span span :timestamp timestamp)))
            hit-ids token-ids))))

(re-frame/register-handler
 :dispatch-bulk-annotation
 standard-middleware
 (fn [db [_ {:keys [ann-data db-path corpus] :or {db-path :query}}]]
   (let [{:keys [ann-map hit-ids token-ids token-to's]} ann-data
         ann-maps (package-ann-maps ann-map hit-ids token-ids token-to's)]
     (re-frame/dispatch [:dispatch-annotation ann-maps corpus :db-path db-path]))
   db))

(re-frame/register-handler
 :dispatch-annotation
 standard-middleware
 (fn [db [_ ann-map-or-maps corpus & {:keys [db-path] :or {db-path :query}}]]
   (let [project-name (get-in db [:session :active-project])]
     (try (POST "/annotation/new"
                {:params (cond-> {:ann-map ann-map-or-maps
                                  :project-name project-name
                                  :corpus (get-corpus-param db project-name db-path corpus)}
                           (= db-path :query) (assoc :query (get-query db project-name)))
                 :handler (dispatch-annotation-handler :db-path db-path)
                 :error-handler error-handler})
          (catch :default e
            (re-frame/dispatch
             [:notify {:message (format "Couldn't dispatch annotation. Reason: [%s]" (str e))}])))
     db)))

(defn update-annotation-handler
  [& {:keys [db-path]}]
  (fn [{status :status message :message payload :data}]
    (condp = status
      :ok (re-frame/dispatch [:add-annotation {:payload payload :db-path db-path}])
      :error (re-frame/dispatch
              [:notify {:message (format "Couldn't update annotation! Reason: [%s]" message)}]))))

(re-frame/register-handler
 :update-annotation
 (fn [db [_ {update-map :update-map db-path :db-path :or {db-path :query}}]]
   (let [project-name (get-in db [:session :active-project])
         path-to-results [:projects project-name :session :query :results]
         corpus (get-in db (into path-to-results [:results-summary :corpus]))
         query (get-in db (into path-to-results [:results-summary :query-str]))
         update-map (assoc update-map :timestamp (.now js/Date) :corpus corpus :query query)]
     (POST "/annotation/update"
           {:params {:update-map update-map :project-name project-name}
            :handler (update-annotation-handler :db-path db-path)
            :error-handler error-handler})
     db)))

(defn remove-annotation-handler [& {:keys [db-path]}]
  (fn [{{project-name :project-name hit-id :hit-id span :span key :key :as data} :data
        status :status message :message}]
    (condp = status
      :ok (re-frame/dispatch [:remove-annotation {:payload data :db-path db-path}])
      :error (re-frame/dispatch
              [:notify {:message (format "Couldn't remove annotation! Reason: [%s]" message)
                        :meta data}]))))

(re-frame/register-handler
 :delete-annotation
 (fn [db [_ {{:keys [ann-map hit-id]} :ann-data db-path :db-path :or {db-path :query}}]]
   (let [project-name (get-in db [:session :active-project])]
     (POST "/annotation/remove"
           {:params {:project-name project-name :hit-id hit-id :ann ann-map}
            :handler (remove-annotation-handler :db-path db-path)
            :error-handler error-handler})
     db)))
