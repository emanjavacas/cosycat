(ns cleebo.routes.blacklab
  (:require [cleebo.utils :refer [->int ->keyword]]
            [cleebo.routes.auth :refer [safe]]
            [buddy.auth :refer [authenticated?]]
            [cleebo.db.annotations :refer [fetch-annotation]]
            [cleebo.blacklab :refer
             [bl-query bl-query-range bl-sort-query bl-sort-range remove-hits!]]
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
        sort-map {:criterion (keyword criterion)
                  :prop-name prop-name}
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
        sort-map {:criterion (keyword criterion)
                  :prop-name prop-name}        
        query-id username]
    (bl-sort-range blacklab corpus from to context sort-map query-id)))

(def blacklab-router
  (safe (fn [{{db :db} :components
              {route :route} :params :as req}]
          (let [{:keys [results] :as out}
                (case (->keyword route)
                  :query (bl-query-route req)
                  :query-range (bl-query-range-route req)
                  :sort-query (bl-sort-query-route req)
                  :sort-range (bl-sort-range-route req))
                results (for [[idx {:keys [hit] :as hit-map}] (map-indexed vector results)
                              :let [from (:id (first hit))
                                    to   (:id (last hit))
                                    anns (fetch-annotation db (->int from) (->int to))
                                    new-hit (map (fn [{:keys [id] :as token}]
                                                   (if-let [token-anns (get anns (->int id))]
                                                     (assoc token :anns (:anns token-anns))
                                                     token))
                                                 hit)]]
                          (assoc hit-map :hit new-hit))]
            {:status 200 :body (assoc out :results results)}))
        {:login-uri "/login" :is-ok? authenticated?}))
