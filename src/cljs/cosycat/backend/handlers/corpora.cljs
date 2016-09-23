(ns cosycat.backend.handlers.corpora
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [cosycat.backend.middleware :refer [standard-middleware]]
            [cosycat.app-utils :refer [update-coll]]
            [taoensso.timbre :as timbre]))

(re-frame/register-handler
 :set-corpus-info
 standard-middleware
 (fn [db [_ corpus-name corpus-info]]
   (update-in
    db
    [:corpora]
    (fn [corpora]
      (update-coll corpora #(= (:corpus %) corpus-name) assoc :info corpus-info)))))
