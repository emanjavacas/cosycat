(ns cosycat.backend.handlers.review
  (:require [re-frame.core :as re-frame]
            [cosycat.app-utils :refer [make-hit-id]]
            [cosycat.backend.middleware :refer [standard-middleware]]
            [cosycat.backend.handlers.annotations :refer [update-hit]]))

(re-frame/register-handler
 :add-review-hit
 standard-middleware
 (fn [db [_ {:keys [id hit] :as hit-map}]]
   ;; watch out for hits belonging to different corpora and equal hit-id's
   (let [active-project (get-in db [:session :active-project])
         path-to-hit-id [:projects active-project :session :review :results :results-by-id id]]
     (-> db
         (assoc-in path-to-hit-id hit-map)
         (assoc-in (into path-to-hit-id [:meta :throbbing?]) false)))))

(re-frame/register-handler
 :add-review-annotation
 standard-middleware
 (fn [db [_ {:keys [hit-id project-name anns]}]]
   (update-in db [:projects project-name :session :review :results :results-by-id hit-id :hit]
              update-hit anns)))

(defn make-results-by-id [grouped-data]
  (apply hash-map (interleave (keys grouped-data) (repeat {:meta {:throbbing? true}}))))

(re-frame/register-handler
 :set-review-results
 standard-middleware
 (fn [db [_ {grouped-data :grouped-data :as results-summary} context]]
   (let [active-project (get-in db [:session :active-project])
         path-to-results [:projects active-project :session :review :results]]
     (re-frame/dispatch [:stop-throbbing :review-frame])
     (doseq [{:keys [hit-id corpus anns]} (vals grouped-data)]
       (re-frame/dispatch [:fetch-review-hit {:hit-id hit-id :corpus corpus :context context}]))
     (-> db
         (assoc-in (conj path-to-results :results-summary) results-summary)      
         (assoc-in (conj path-to-results :results-by-id) (make-results-by-id grouped-data))))))

(re-frame/register-handler
 :unset-review-results
 standard-middleware
 (fn [db _]
   (let [active-project (get-in db [:session :active-project])
         path-to-results [:projects active-project :session :review :results]]
     (-> db
         (assoc-in (conj path-to-results :results-summary) {})
         (assoc-in (conj path-to-results :results-by-id) {})))))
