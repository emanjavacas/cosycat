(ns cleebo.routes.cqp
  (:require [taoensso.timbre :as timbre]
            [cleebo.utils :refer [->int ->keyword]]
            [cleebo.routes.auth :refer [safe]]
            [buddy.auth :refer [authenticated?]]
            [cleebo.components.cqp :refer [cqi-query cqi-query-range]]))

(defn cqp-query-route
  [{{cqi-client :cqi-client} :components
    {corpus :corpus query-str :query-str context :context size :size from :from} :params}]
  (let [from (->int from)
        to (+ from (->int size))]
    (cqi-query cqi-client corpus query-str from to (->int context))))

(defn cqp-query-range-route
  [{{cqi-client :cqi-client} :components
    {corpus :corpus from :from to :to context :context} :params}]
  (cqi-query-range cqi-client corpus (->int from) (->int to) (->int context)))

(def cqp-router
  (safe (fn [{{route :route} :params :as req}]
          (let [out (case (->keyword route)
                      :query (cqp-query-route req)
                      :query-range (cqp-query-range-route req))]
            {:status 200 :body out}))
        {:login-uri "/login" :is-ok? authenticated?}))
