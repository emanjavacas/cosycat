(ns cleebo.routes.notifications)

(defn notify-route [ws payload]
  ;; todo {:post [(s/validate ws-from-server (:payload %)))]}
  (let [{ws-from :ws-from payload :payload} payload]
    {:ws-target ws-from :ws-from ws-from :payload {:type :notify :data {}}}))
