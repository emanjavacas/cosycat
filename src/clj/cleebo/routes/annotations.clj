(ns cleebo.routes.annotations
  (:require [schema.core :as s]
            [cleebo.db.annotations :refer [new-token-annotation]]
            [cleebo.shared-schemas :refer [ws-from-client ws-from-server]]
            [taoensso.timbre :as timbre]))

(defn map-vals
  "applies a function `f` over the values of a map"
  [f m]
  (into {} (for [[k v] m] [k (f v)])))

(defn transpose
  "groups maps by key using `f` on the coll of vals of each key"
  [f & mlist]
  (into {} (map (fn [[k v]]
                  [k (apply f (map val v))])
                (group-by key (apply concat mlist)))))

;; (defn aggregate-ok [db-payload]
;;   (update db-payload :ok (partial (apply transpose vector))))

;; (defn aggregate-error [db-payload]
;;   (update-in db-payload :error ))

(defmulti response-payload
  "process the variadic output of an annotation insert
   returning the final data payload for the client(s)"
  (fn dispatch-fn [db-payload hit-id] (type db-payload)))

(s/defmethod response-payload
  clojure.lang.PersistentArrayMap
  [db-payload hit-id :- s/Int]
  (assoc-in db-payload [:data :hit-id] hit-id))

(s/defmethod response-payload
  clojure.lang.PersistentVector
  [db-payload hit-id]
  (let [hit-ids (if (vector? hit-id) hit-id (repeat hit-id))
        data-keys [:ann :scope :reason :e :hit-id]]
    (->> db-payload
         (map (fn [hit-id payload] (assoc-in payload [:data :hit-id] hit-id)) hit-ids)
         (group-by :status)
         (map-vals (partial map (fn [{:keys [data]}] (select-keys data data-keys))))
         (map-vals (partial apply transpose vector))
         (map (fn [[status data]] {:data data :type :annotation :status status}))
         vec)))

(defn annotation-route [ws client-payload]
  (let [{ws-from :ws-from {:keys [type status data]} :payload} client-payload
        {hit-id :hit-id ann :ann} data
        {db :db} ws
        db-payload (new-token-annotation db ann)
        server-payload (response-payload db-payload hit-id)]
    ;; eventually notify other clients of the new annotation
    ;; (let [clients @(:clients ws)
    ;;       {:keys [ws-in]} (:chans ws)]
    ;;   (put! ws-in {:ws-from ws-from :payload {:type :notify :data {}}}))
    (if (map? server-payload)
      {:ws-target ws-from :ws-from ws-from :payload server-payload}
      (vec (for [p server-payload]
             {:ws-target ws-from :ws-from ws-from :payload p})))))

;; (require '[schema-generators.generators :as g]
;;          '[cleebo.shared-schemas :refer [annotation-schema]]
;;          '[cleebo.components.db :refer [new-db]]
;;          '[clojure.test.check.generators :as check-generators])

;; (def force-int ((g/fmap inc) check-generators/int))
;; (defn create-dummy-annotation [username & [n]]
;;   (let [anns (map (fn [m] (assoc m :username username))
;;                   (g/sample (+ 5 (or n 0)) annotation-schema {s/Int force-int}))]
;;     (if n
;;       (vec (take n anns))
;;       (peek (vec anns)))))

;(def db (.start (new-db {:url "mongodb://127.0.0.1:27017/cleeboTest"})))

;; (def ann (create-dummy-annotation "user" 3))
;; (identity ann)
;; (def x (new-token-annotation db ann))
;; (identity x)
;(s/validate [annotation-schema]  (:ann (:data (first y))))

