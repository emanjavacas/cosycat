(ns cleebo.backend.history
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [cleebo.utils :refer [timestamp]]
            [cleebo.backend.middleware :refer [standard-middleware]]))

(re-frame/register-handler
 :register-history
 standard-middleware
 (fn [db [_ path payload]]
   (update-in db (concat [:history] path) conj (assoc payload :received (timestamp)))))
