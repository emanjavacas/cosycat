(ns cosycat.backend.handlers.review
  (:require [re-frame.core :as re-frame]
            [cosycat.backend.middleware :refer [standard-middleware]]
            [cosycat.backend.handlers.annotations :refer [update-hit]]))

(re-frame/register-handler
 :add-review-hit
 standard-middleware
 (fn [db [_ {:keys [id] :as hit-map}]]
   ;; watch out for hits belonging to different corpora and equal hit-id's
   (let [active-project (get-in db [:session :active-project])
         path-to-hit-id [:projects active-project :session :review :results :results-by-id id]]
     (assoc-in db path-to-hit-id hit-map))))

(re-frame/register-handler
 :add-review-annotation
 standard-middleware
 (fn [db [_ {:keys [hit-id project-name anns]}]]
   (update-in db [:projects project-name :session :review :results :results-by-id hit-id :hit]
              update-hit anns)))
