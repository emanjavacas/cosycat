(ns cleebo.backend.handlers.settings
  (:require [re-frame.core :as re-frame]))

(re-frame/register-handler
 :set-session
 (fn [db [_ path value]]
   (let [session (:session db)]
     (assoc db :session (assoc-in session path value)))))
