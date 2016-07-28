(ns cleebo.backend.handlers.corpora
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [cleebo.app-utils :refer [update-coll]]
            [taoensso.timbre :as timbre]))

(re-frame/register-handler
 :set-corpus-info
 standard-middleware
 (fn [db [_ corpus-name corpus-info]]
   (update-in
    db
    [:corpora]
    (fn [corpora]
      (update-coll corpora #(= (:name %) corpus-name) assoc :info corpus-info)))))
