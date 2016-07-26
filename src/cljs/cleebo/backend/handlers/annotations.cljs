(ns cleebo.backend.handlers.annotations
  (:require [re-frame.core :as re-frame]
            [schema.core :as s]
            [ajax.core :refer [POST GET]]
            [cleebo.schemas.annotation-schemas :refer [annotation-schema]]
            [cleebo.app-utils :refer [deep-merge]]
            [cleebo.utils :refer [->int format get-msg get-token-id]]
            [cleebo.backend.middleware :refer [standard-middleware no-debug-middleware]]
            [taoensso.timbre :as timbre]))

;; (POST "/annotation/update"
;;      {:params {:project "beat"
;;                :update-map {:_id "2eea61e3-8e40-491e-a730-ad25afb7c578"
;;                             :_version 6
;;                             :query "\"a\""
;;                             :corpus "mbg-small"
;;                             :ann {:value "no"}}
;;                :hit-id "0"}
;;       :handler #(.log js/console "SUCCESS" %)
;;       :error-handler #(.log js/console "ERROR" %)})

;; (GET "/annotation/range"
;;      {:params {:project "project3" :from 0 :size 20 :hit-id "Hi!"}
;;       :handler #(.log js/console %)
;;       :error-handler #(.log js/console "ERROR" %)})

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
      (update-in db path update-hit anns)
      (if-let [hit-id (find-hit-id (keys anns) (vals results-by-id))]
        (update-in db path update-hit anns)
        db))))

(defmethod add-annotations cljs.core/PersistentVector
  [db ms]
  (reduce (fn [db m] (add-annotations db m)) db ms))

(re-frame/register-handler              ;generic handler
 :add-annotation
 standard-middleware
 (fn [db [_ map-or-maps]] (add-annotations db map-or-maps)))

(re-frame/register-handler
 :fetch-annotations
 (fn [db [_ {:keys [starts ends hit-ids] :as params}]]
   (let [project (get-in db [:session :active-project])]
     (GET "/annotation/page"
          {:params (assoc params :project project)
           :handler #(re-frame/dispatch [:add-annotation %])
           :error-handler #(.log js/console "Couldn't fetch anns" %)}))
   db))

;;; Outgoing annotations
(s/defn ^:always-validate make-annotation :- annotation-schema
  ([ann-map token-id :- s/Int]
   (assoc ann-map :span {:type "token" :scope token-id} :timestamp (.now js/Date)))
  ([ann-map token-from :- s/Int token-to :- s/Int]
   {:pre [(>= token-to token-from)]}
   (assoc ann-map :span {:type "IOB" :scope {:B token-from :O token-to}} :timestamp (.now js/Date))))

(defmulti package-annotation
  "packages annotation data for the server. It only supports bulk payloads for token annotations"
  (fn [ann-map-or-maps project hit-id token-id & [token-to]]
    [(type ann-map-or-maps) (type token-id)]))

(s/defmethod package-annotation
  [cljs.core/PersistentArrayMap js/Number]
  ([ann-map project hit-id :- s/Int token-id :- s/Int]
   (let [ann-map (make-annotation ann-map token-id)]
     {:hit-id hit-id
      :project project
      :ann-map ann-map}))
  ([ann-map project hit-id :- s/Int token-from :- s/Int token-to :- s/Int]
   (let [ann-map (make-annotation ann-map token-from token-to)]
     {:hit-id hit-id
      :project project
      :ann-map ann-map})))

(s/defmethod package-annotation
  [cljs.core/PersistentArrayMap cljs.core/PersistentVector]
  [ann-map project hit-ids :- [s/Int] token-ids :- [s/Int]]
  (let [ann-maps (mapv (fn [token-id] (make-annotation ann-map token-id)) token-ids)]
    {:hit-id hit-ids
     :project project
     :ann-map ann-maps}))

(s/defmethod package-annotation
  [cljs.core/PersistentVector cljs.core/PersistentVector]
  [anns project hit-ids :- [s/Int] token-ids :- [s/Int]]
  {:pre [(apply = (map count [anns hit-ids]))]}
  (let [ann-maps (mapv (fn [ann token-id] (make-annotation ann token-id)) anns token-ids)]
    {:hit-id hit-ids
     :project project
     :ann-map ann-maps}))

(defmulti dispatch-annotation-handler
  "Variadic handler for successful annotations. Dispatches are based on whether
  ann-map is a vector (bulk annotation payload) or a map (single annotation payload)"
  type)

(defmethod dispatch-annotation-handler cljs.core/PersistentArrayMap
  [{status :status
    message :message                    ;error message in case of error
    {project :project hit-id :hit-id anns :anns ;success payload
     {{B :B O :O :as scope} :scope type :type} :span ;error payload
     :as m} :data :as data}]
  (.log js/console data)
  (case status
    :ok (re-frame/dispatch [:add-annotation m])
    :error (re-frame/dispatch
            [:notify {:message (case type
                                 "token" (get-msg [:annotation :error :token] scope message)
                                 "IOB" (get-msg [:annotation :error :IOB] B O message))}])))

(defmethod dispatch-annotation-handler cljs.core/PersistentVector
  [ms]
  (doseq [m ms] (dispatch-annotation-handler m)))

(defn error-handler [& args]
  (re-frame/dispatch [:notify {:message "Unrecognized internal error"}]))

(re-frame/register-handler
 :dispatch-annotation
 (fn [db [_ ann & args]]
   (let [project (get-in db [:session :active-project])
         username (get-in db [:me :username])
         corpus (get-in db [:projects project :session :query :results-summary :corpus])
         query (get-in db [:projects project :session :query :results-summary :query-str])         
         ann-map {:ann ann :username username :corpus corpus :query query}]
     (try (POST "/annotation/new"
                {:params (apply package-annotation ann-map project args)
                 :handler dispatch-annotation-handler
                 :error-handler dispatch-annotation-handler})
          (catch :default e
            (re-frame/dispatch
             [:notify {:message (format "Couldn't dispatch annotation: %s" (str e))}])))
     db)))

