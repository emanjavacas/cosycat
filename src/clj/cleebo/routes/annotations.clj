(ns cleebo.routes.annotations
  (:require [schema.core :as s]
            [cleebo.db.annotations :refer [new-token-annotation]]
            [cleebo.components.ws :refer [notify-clients]]
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
        data-keys [:ann-map :scope :reason :e :hit-id]]
    (->> db-payload
         (map (fn [hit-id payload] (assoc-in payload [:data :hit-id] hit-id)) hit-ids)
         (group-by :status)
         (map-vals (partial map (fn [{:keys [data]}] (select-keys data data-keys))))
         (map-vals (partial apply transpose vector))
         (map (fn [[status data]] {:data data :type :annotation :status status}))
         vec)))

(defn annotation-route [ws client-payload]
  (let [{ws-from :ws-from {:keys [type status data]} :payload} client-payload
        {hit-id :hit-id ann-map :ann-map} data
        {db :db} ws
        db-payload (new-token-annotation db (assoc ann-map :username ws-from))
        server-payload (response-payload db-payload hit-id)]
    (if (map? server-payload)
      (do (notify-clients ws server-payload :ws-from ws-from)
          {:ws-target ws-from :ws-from ws-from :payload server-payload})
      (vec (for [p server-payload]
             (do (notify-clients ws p :ws-from ws-from)
                 {:ws-target ws-from :ws-from ws-from :payload p}))))))

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

