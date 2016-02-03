(ns cleebo.routes.cqp
  (:require [cleebo.utils :refer [->int]]
            [cleebo.routes.auth :refer [safe]]
            [buddy.auth :refer [authenticated?]]
            [cleebo.cqp :refer [cqi-query cqi-query-range]]))

(defn cqp-query-route
  [{{cqi-client :cqi-client} :components
    {corpus :corpus query-str :query-str context :context size :size from :from} :params}]
  (cqi-query
   {:cqi-client cqi-client :corpus corpus :query-str query-str
    :opts {:context (->int context) :size (->int size) :from (->int from)}}))

(defn cqp-query-range-route
  [{{cqi-client :cqi-client} :components
    {corpus :corpus from :from to :to context :context} :params}]
  (cqi-query-range
   {:cqi-client cqi-client :corpus corpus
    :from (->int from) :to (->int to)
    :opts {:context (->int context)}}))

(def cqp-router
  (safe (fn [{{route :route} :params :as req}]
          (let [out (case route
                      :query (cqp-query-route req)
                      :query-range (cqp-query-range-route req))]
            {:status 200 :body out}))
        {:login-uri "/login" :is-ok? authenticated?}))
