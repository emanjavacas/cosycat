(ns cosycat.db.utils
  (:require [cosycat.app-utils :refer [dekeyword]]
            [cosycat.components.db :refer [colls]]
            [monger.operators :refer :all]
            [monger.collection :as mc]))

(defn ->set-update-map
  "transforms a db update-map into a proper mongodb update document to be passed as value of $set"
  [prefix update-map]
  (reduce-kv (fn [m k v]
               (-> m
                   (assoc (str prefix "." (dekeyword k)) v)
                   (dissoc k)))
             {}
             update-map))

(defn normalize-user
  "transforms db user doc into public user (no private info)"
  [user & ks]
  (-> (apply dissoc user :password :_id ks)
      (update-in [:roles] (partial apply hash-set))))

(defn normalize-project [{:keys [issues events] :as project}]
  (cond-> project
    true (dissoc :_id)))

(defn is-user?
  [{db-conn :db :as db} {:keys [username email]}]
  (some-> (mc/find-one-as-map db-conn (:users colls) {$or [{:username username} {:email email}]})
          (normalize-user :settings)))
