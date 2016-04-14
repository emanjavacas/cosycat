(ns cleebo.backend.handlers.session
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [ajax.core :refer [GET]]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [taoensso.timbre :as timbre]))

(defn session-handler [data]
  (timbre/debug data))

(re-frame/register-handler
 :fetch-user-session
 standard-middleware
 (fn [db _]
   (GET "/session"
        {:handler session-handler
         :error-handler session-handler})
   db))


