(ns cosycat.db.utils
  (:require [cosycat.app-utils :refer [dekeyword normalize-by]]
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

(defn hit-id->mongo-id
  [hit-id & {:keys [to-escape escape] :or {to-escape "." escape "-"}}]
  (assert (not (re-find #"-" hit-id)) (str "hit-id cannot contain escape character [\""  escape "\"]"))
  (clojure.string/replace hit-id to-escape escape))

(defn mongo-id->hit-id
  [mongo-id & {:keys [to-escape escape] :or {to-escape "." escape "-"}}]
  (let [is-keyword? (keyword? mongo-id)]
    (cond-> mongo-id
      is-keyword? dekeyword
      true (clojure.string/replace escape to-escape))))

(defn normalize-user
  "transforms db user doc into public user (no private info)"
  [user & ks]
  (-> (apply dissoc user :password :_id ks)
      (update-in [:roles] (partial apply hash-set))))

(defn normalize-project-queries [{:keys [queries] :as project}]
  (assoc project :queries (normalize-by queries :id)))

(defn normalize-query-hit [query-hit]
  (dissoc query-hit :_id :query-id :project-name))

(defn normalize-project [{:keys [issues events queries] :as project}]
  (cond-> project
    true (dissoc :_id)
    queries normalize-project-queries))

(defn is-user?
  [{db-conn :db :as db} {:keys [username email]}]
  (some-> (mc/find-one-as-map db-conn (:users colls) {$or [{:username username} {:email email}]})
          (normalize-user :settings)))
