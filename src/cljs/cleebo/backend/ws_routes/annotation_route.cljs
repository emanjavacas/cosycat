(ns cleebo.backend.ws-routes.annotation-route
  (:require [re-frame.core :as re-frame]
            [cleebo.shared-schemas :refer [annotation-schema]]
            [cleebo.utils :refer [update-token]]
            [cleebo.backend.middleware
             :refer [standard-middleware no-debug-middleware db-schema]]))

(re-frame/register-handler
 :annotate
 standard-middleware
 (fn [db [_ {:keys [hit-id token-id ann]}]]
   (let [hit-map (get-in db [:session :results-by-id hit-id])
         token-fn (fn [token] (update token :anns #(concat % [ann])))]
     (re-frame/dispatch
      [:sent-ws
       {:type :annotation
        :data {:cpos token-id
               :ann ann}}])
     (assoc-in
      db
      [:session :results-by-id hit-id]
      (update-token hit-map token-id token-fn)))))
