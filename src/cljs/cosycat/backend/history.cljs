(ns cosycat.backend.history
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [cosycat.utils :refer [timestamp]]
            [cosycat.backend.middleware :refer [standard-middleware]]))

(re-frame/register-handler
 :register-history
 standard-middleware
 (fn [db [_ path payload]]
   (update-in db (concat [:history] path) conj (assoc payload :received (timestamp)))))
