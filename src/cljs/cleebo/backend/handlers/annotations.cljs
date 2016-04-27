(ns cleebo.backend.handlers.annotations
  (:require [re-frame.core :as re-frame]
            [schema.core :as s]
            [ajax.core :refer [POST]]
            [cleebo.schemas.annotation-schemas :refer [annotation-schema]]
            [cleebo.utils :refer [->int format get-msg]]
            [cleebo.backend.middleware
             :refer [standard-middleware no-debug-middleware]]
            [taoensso.timbre :as timbre]))

(re-frame/register-handler
 :mark-hit
 standard-middleware
 (fn [db [_ {:keys [hit-id flag]}]]
   (assoc-in db [:session :results-by-id hit-id :meta :marked] (boolean flag))))

(re-frame/register-handler
 :mark-all-hits
 standard-middleware
 (fn [db _]
   (reduce
    (fn [acc-db hit-id]
      (assoc-in acc-db [:session :results-by-id hit-id :meta :marked] true))
    db
    (get-in db [:session :results]))))

(re-frame/register-handler
 :demark-all-hits
 standard-middleware
 (fn [db _]
   (reduce
    (fn [acc-db hit-id]
      (assoc-in acc-db [:session :results-by-id hit-id :meta :marked] false))
    db
    (keys (get-in db [:session :results-by-id])))))

(defn has-marked?
  "for a given hit-map we look if the current (de)marking update
  leave behind marked tokens. The result will be false if no marked
  tokens remain, otherwise it returns the token-id of the token marked"
  [{:keys [hit meta] :as hit-map} flag token-id]
  (if flag
    true
    (let [marked-tokens (filter :marked hit)]
      (case (count marked-tokens)
        0 (throw (js/Error. "Trying to demark a token, but no marked tokens found"))
        1 (do (assert (= token-id (:id (first marked-tokens)))) false)
        (some #(= token-id %) (map :id marked-tokens))))))

(defn update-token
  "apply token-fn where due"
  [{:keys [hit meta] :as hit-map} check-token-fn token-fn]
  (assoc hit-map :hit (map (fn [{:keys [id] :as token}]
                             (if (check-token-fn id)
                               (token-fn token)
                               token))
                           hit)))

(re-frame/register-handler
 :mark-token
 standard-middleware
 (fn [db [_ {:keys [hit-id token-id flag]}]]
   (let [hit-map (get-in db [:session :results-by-id hit-id])
         has-marked (has-marked? hit-map flag token-id)
         hit-map (assoc-in hit-map [:meta :has-marked] (boolean has-marked))
         check-token-fn (fn [id] (= token-id id))
         token-fn (fn [tk] (if flag (assoc tk :marked true) (dissoc tk :marked)))]
     (assoc-in
      db
      [:session :results-by-id hit-id]
      (update-token hit-map check-token-fn token-fn)))))

(defmulti update-token-anns
  "inserts incoming annotation into the corresponding hit map"
  (fn [hit-map {{scope :scope t :type} :span}] t))
(defmethod update-token-anns "token"
  [hit-map {{scope :scope} :span project-name :project {k :key} :ann :as ann-map}]
  (let [token-fn (fn [token] (assoc-in token [:anns project-name k] ann-map))
        check-token-fn (fn [id] (= (str scope) id))]
    (update-token hit-map check-token-fn token-fn)))
(defmethod update-token-anns "IOB"
  [hit-map {{scope :scope} :span project-name :project {k :key} :ann :as ann-map}]
  (let [{B :B O :O} scope
        token-fn (fn [token] (assoc-in token [:anns project-name k] ann-map))
        check-fn (fn [id] (contains? (apply hash-set (range B (inc O))) (->int id)))]
    (update-token hit-map check-fn token-fn)))

(defn- find-ann-hit-id*
  ([pred hit-maps]
   (some (fn [{:keys [hit meta id]}]
           (when (some pred (map :id hit))
             id))
         hit-maps)))

(defmulti find-ann-hit-id (fn [{{type :type} :span} hit-id] type))
(defmethod find-ann-hit-id "token"
  [{{scope :scope} :span} hit-maps]
  (find-ann-hit-id* #{(str scope)} hit-maps))
(defmethod find-ann-hit-id "IOB"
  [{{{B :B O :O} :scope} :span} hit-maps]
  (find-ann-hit-id* (into #{} (map str (range B (inc O)))) hit-maps))

(defmulti compute-notification-data     ;TODO: shortcut if not in project
  (fn [{:keys [ann-map hit-id]} me] (type ann-map)))
(defmethod compute-notification-data cljs.core/PersistentArrayMap
  [{{{{B :B O :B :as scope} :scope type :type} :span
     project :project user :username} :ann-map} me]
  (let [by (if (= me user) :me :other)]
    {:message (case [type user]
                ["token" me] (get-msg [:annotation :ok by :token] scope)
                ["IOB" me] (get-msg [:annotation :ok by :IOB] B O)
                ["token" user] (get-msg [:annotation :ok by :token] user scope)
                ["IOB" user] (get-msg [:annotation :ok by :IOB] user B O))
     :by user}))
(defmethod compute-notification-data cljs.core/PersistentVector
  [{:keys [ann-map]} me]
  (let [username (:username (first ann-map))] ;assumes all anns were made by same user
    {:message (if (= me username)
                (get-msg [:annotation :ok :me :mult] (count ann-map))
                (get-msg [:annotation :ok :other :mult] username (count ann-map)))
     :by username}))

(re-frame/register-handler
 :add-annotation
 standard-middleware
 (fn [db [_ {:keys [hit-id ann-map] :as data}]]
   (let [me (get-in db [:session :user-info :username])
         {:keys [message by]} (compute-notification-data data me)
         results-by-id (get-in db [:session :results-by-id])
         hit-id (if (contains? results-by-id hit-id) hit-id
                    (find-ann-hit-id ann-map (vals results-by-id)))]
     (if-let [hit-map (get results-by-id hit-id)]
       (do (re-frame/dispatch [:notify {:message message :by by}])
           (assoc-in
            db
            [:session :results-by-id hit-id]
            (update-token-anns hit-map ann-map)))
       (do (timbre/debug "couldn't find hit") db)))))

(s/defn ^:always-validate make-annotation :- annotation-schema
  ([username project ann token-id]
   {:ann ann
    :username username
    :project project
    :span {:type "token"
           :scope token-id}
    :timestamp (.now js/Date)})
  ([username project ann token-from :- s/Int token-to :- s/Int]
   {:pre [(>= token-to token-from)]}
   {:ann ann
    :username username
    :project project
    :span {:type "IOB"
           :scope {:B token-from
                   :O token-to}}
    :timestamp (.now js/Date)}))

(defmulti package-annotation
  "packages annotation data for sending it to server. It only supports bulk payloads
  for token annotations"
  (fn [username project ann hit-id token-id & [token-to]]
    [(type ann) (type token-id)]))

(s/defmethod package-annotation
  [cljs.core/PersistentArrayMap js/Number]
  ([username project ann hit-id :- s/Int token-id :- s/Int]
   (let [ann-map (make-annotation username project ann token-id)]
     {:hit-id hit-id
      :ann-map ann-map}))
  ([username project ann hit-id :- s/Int token-from :- s/Int token-to :- s/Int]
   (let [ann-map (make-annotation username project ann token-from token-to)]
     {:hit-id hit-id
      :ann-map ann-map})))

(s/defmethod package-annotation
  [cljs.core/PersistentArrayMap cljs.core/PersistentVector]
  [username project ann hit-ids :- [s/Int] token-ids :- [s/Int]]
  (let [ann-maps (mapv (fn [token-id]
                         (make-annotation username project ann token-id))
                       token-ids)]
    {:hit-id hit-ids
     :ann-map ann-maps}))

(s/defmethod package-annotation
  [cljs.core/PersistentVector cljs.core/PersistentVector]
  [username project anns hit-ids :- [s/Int] token-ids :- [s/Int]]
  {:pre [(apply = (map count [anns hit-ids]))]}
  (let [ann-maps (mapv (fn [ann token-id]
                         (make-annotation username project ann token-id))
                       anns
                       token-ids)]
    {:hit-id hit-ids
     :ann-map ann-maps}))

(defmulti handler
  "Variadic handler for successfull annotations. Dispatches are based on whether
  ann-map is a vector (bulk annotation payload) or a map (single annotation payload)"
  type)
(defmethod handler cljs.core/PersistentArrayMap
  [{status :status {hit-id :hit-id ann-map :ann-map :as data} :data
    {{{B :B O :O :as scope} :scope} :scope type :type} :span reason :reason e :e}]
  (case status
    :ok (re-frame/dispatch [:add-annotation {:hit-id hit-id :ann-map ann-map}])
    :error (re-frame/dispatch
            [:notify {:message (case type
                                 "token" (get-msg [:annotation :error :token] scope e) 
                                 "IOB" (get-msg [:annotation :error :IOB] B O e))}])))
(defmethod handler cljs.core/PersistentVector
  [payloads]
  (doseq [payload payloads]
    (handler payload)))

(defn error-handler [& args]
  (re-frame/dispatch [:notify {:message "Unrecognized internal error"}]))

(re-frame/register-handler
 :dispatch-annotation
 (fn [db [_ & args]]
   (let [{project-name :name} (get-in db [:session :active-project])
         username (get-in db [:session :user-info :username])]
     (try (POST "/annotation"
                {:params (apply package-annotation username project-name args)
                 :handler handler
                 :error-handler error-handler})
          (catch :default e
            (re-frame/dispatch
             [:notify {:message (format "Couldn't dispatch annotation: %s" (str e))}])))
     db)))

