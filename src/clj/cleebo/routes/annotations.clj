(ns cleebo.routes.annotations
  (:require [schema.core :as s]
            [cleebo.db.annotations :refer [new-token-annotation]]
            [cleebo.shared-schemas :refer [ws-from-server]]))

(defn response-payload []
  )

(defn annotation-route [ws payload]
  {:pre [(s/validate (ws-from-server payload) payload)]}
  (let [{ws-from :ws-from {:keys [type status data]} :payload} payload
        {token-id :token-id hit-id :hit-id ann :ann} data]
    (try
      (let [response-payload (new-token-annotation (:db ws) token-id ann) ;anns|vec of anns
            payload {:data {:token-id token-id :hit-id hit-id :anns response-payload}
                     :status :ok
                     :type :annotation}]
        ;; eventually notify other clients of the new annotation
        {:ws-target ws-from :ws-from ws-from :payload payload})
      (catch Exception e
        (let [payload {:status :error :type :annotation
                       :data {:token-id token-id
                              :reason :internal-error
                              :e (str e)}}]
          {:ws-target ws-from :ws-from ws-from :payload payload})))))
