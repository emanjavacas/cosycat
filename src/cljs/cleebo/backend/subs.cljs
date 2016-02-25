(ns cleebo.backend.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]
              [cleebo.utils :refer [filter-marked-hits select-values]]
              [taoensso.timbre :as timbre]))

(re-frame/register-sub
 :db
 (fn [db _]
   (reaction @db)))

(re-frame/register-sub
 :open-init-modal
 (fn [db _]
   (reaction (:init-modal @db))))

(re-frame/register-sub
 :active-panel
 (fn [db _]
   (reaction (:active-panel @db))))

(re-frame/register-sub
 :notifications
 (fn [db _]
   (let [notifications (reaction (:notifications @db))]
     (reaction (reverse
                (sort-by (fn [{:keys [date]}] date)
                         (for [[_ notification] @notifications]
                           notification)))))))

(re-frame/register-sub
 :throbbing?
 (fn [db [_ panel]]
   (reaction (get-in @db [:throbbing? panel] false))))

(re-frame/register-sub
 :session
 (fn [db [_ & path]]
   (let [session (reaction (:session @db))]
     (reaction (get-in @session path)))))

(re-frame/register-sub
 :settings
 (fn [db [_ & path]]
   (let [settings (reaction (:settings @db))]
     (reaction (get-in @settings path)))))

(re-frame/register-sub
 :query-opts
 (fn [db _]
   (reaction (get-in @db [:session :query-opts]))))

(re-frame/register-sub
 :query-results
 (fn [db _]
   (reaction (get-in @db [:session :query-results]))))

(re-frame/register-sub
 :results
 (fn [db _]
   (let [results (reaction (get-in @db [:session :results]))
         results-by-id (reaction (get-in @db [:session :results-by-id]))]
     (reaction (select-values @results-by-id @results)))))

(re-frame/register-sub ;all marked hits, also if currently not in table-results
 :marked-hits
 (fn [db [_ {:keys [has-marked?]}]]
   (let [results-by-id (reaction (get-in @db [:session :results-by-id]))]
     (reaction (filter-marked-hits @results-by-id :has-marked? has-marked?)))))

(re-frame/register-sub
 :marked-tokens
 (fn [db _]
   (let [results-by-id (reaction (get-in @db [:session :results-by-id]))]
     (reaction (mapcat (fn [[_ {:keys [hit id meta]}]]
                         (->> hit
                              (filter :marked)
                              (map #(assoc % :hit-id id))))
                       @results-by-id)))))

(re-frame/register-sub
 :marked-tokens-in-hit
 (fn [db [_ hit-id]]
   (let [results-by-id (reaction (get-in @db [:session :results-by-id hit-id]))]
     (reaction (filter :marked @results-by-id)))))

(re-frame/register-sub
 :current-annotation-hit
 (fn [db _]
   (reaction (get-in @db [:annotations :current-annotation-hit]))))

(re-frame/register-handler
 :current-annotation-token
 (fn [db _]
   (reaction (get-in db [:annotations :current-annotation-token]))))

(re-frame/register-sub
 :msgs
 (fn [db _]
   (reaction (:msgs @db))))
