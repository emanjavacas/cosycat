(ns cleebo.routes.blacklab
  (:require [cleebo.utils :refer [->int ->keyword]]
            [cleebo.routes.auth :refer [safe]]
            [buddy.auth :refer [authenticated?]]
            [cleebo.db.annotations :refer [merge-annotations]]
            [cleebo.components.blacklab :refer
             [bl-query bl-query-range bl-sort-query bl-sort-range bl-snippet remove-hits!]]
            [taoensso.timbre :as timbre]))

(defn bl-query-route
  [{{{username :username} :identity} :session 
    {blacklab :blacklab} :components
    {corpus :corpus query-str :query-str
     context :context size :size from :from} :params}]
  (let [from (->int from)
        to (+ from (->int size))
        context (->int context)
        query-id username]
    (bl-query blacklab corpus query-str from to context query-id)))

(defn bl-query-range-route
  [{{{username :username} :identity} :session 
    {blacklab :blacklab} :components
    {corpus :corpus context :context to :to from :from} :params}]
  (let [from (->int from)
        to (->int to)
        context (->int context)
        query-id username]
    (bl-query-range blacklab corpus from to context query-id)))

(defn bl-sort-query-route
  [{{{username :username} :identity} :session 
    {blacklab :blacklab} :components
    {corpus :corpus query-str :query-str context :context to :to from :from
     {criterion :criterion prop-name :prop-name} :sort-map :as sort-map} :params}]
  (let [from (->int from)
        to (->int to)
        context (->int context)
        sort-map {:criterion (keyword criterion) :prop-name prop-name}
        query-id username]
    (bl-sort-query blacklab corpus from to context sort-map query-id)))

(defn bl-sort-range-route
  [{{{username :username} :identity} :session 
    {blacklab :blacklab} :components
    {corpus :corpus query-str :query-str context :context to :to from :from
     {criterion :criterion prop-name :prop-name} :sort-map :as sort-map} :params}]
  (let [from (->int from)
        to (->int to)
        context (->int context)
        sort-map {:criterion (keyword criterion) :prop-name prop-name}        
        query-id username]
    (bl-sort-range blacklab corpus from to context sort-map query-id)))

(defn bl-snippet-route
  [{{{username :username} :identity} :session
    {blacklab :blacklab} :components
    {hit-idx :hit-idx snippet-size :snippet-size} :params}]
  (let [query-id username
        hit-idx (->int hit-idx)
        snippet-size (->int snippet-size)]
    (bl-snippet blacklab hit-idx snippet-size query-id)))

(defn with-results-annotations [body db]
  (let [{:keys [results] :as out} body]
    (assoc body :results (merge-annotations db results))))

(defmulti blacklab-routes
  (fn [{{route :route} :params :as req}]
    (->keyword route)))

(defmethod blacklab-routes :query [{{db :db} :components :as req}]
  (with-results-annotations (bl-query-route req) db))

(defmethod blacklab-routes :query-range [{{db :db} :components :as req}]
  (with-results-annotations (bl-query-range-route req) db))

(defmethod blacklab-routes :sort-query [{{db :db} :components :as req}]
  (with-results-annotations (bl-sort-query-route req) db))

(defmethod blacklab-routes :sort-range [{{db :db} :components :as req}]
  (with-results-annotations (bl-sort-range-route req) db))

(defmethod blacklab-routes :snippet [req]
  (bl-snippet-route req))

(def blacklab-router
  (safe (fn [req] {:status 200 :body (blacklab-routes req)})
        {:login-uri "/login" :is-ok? authenticated?}))
