(ns cleebo.backend.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]
              [cleebo.utils :refer [filter-marked-hits select-values dominant-color]]
              [taoensso.timbre :as timbre]))

(re-frame/register-sub
 :db
 (fn [db _]
   (reaction @db)))

(re-frame/register-sub
 :modals
 (fn [db [_ modal]]
   (reaction (get-in @db [:session :modals modal]))))

(re-frame/register-sub
 :active-panel
 (fn [db _]
   (reaction (get-in @db [:session :active-panel]))))

(re-frame/register-sub
 :notifications
 (fn [db _]
   (let [notifications (reaction (get-in @db [:session :notifications]))]
     (reaction (reverse
                (sort-by (fn [{:keys [date]}] date)
                         (for [[_ notification] @notifications]
                           notification)))))))

(re-frame/register-sub
 :throbbing?
 (fn [db [_ panel]]
   (reaction (get-in @db [:session :throbbing? panel] false))))

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
 :has-query?
 (fn [db _]
   (reaction (not (zero? (get-in @db [:session :query-results :query-size]))))))

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
     (reaction (vals (filter-marked-hits @results-by-id :has-marked? has-marked?))))))

(re-frame/register-sub
 :marked-tokens
 (fn [db _]
   (let [results-by-id (reaction (get-in @db [:session :results-by-id]))]
     (reaction (mapcat (fn [[_ {:keys [hit id meta]}]]
                         (->> hit
                              (filter :marked)
                              (map #(assoc % :hit-id id))))
                       @results-by-id)))))

(defn get-project-info [db project-name]
  (first (filter #(= project-name (:name %))
                 (get-in db [:session :user-info :projects]))))

(defn get-all-users-info
  ([db] (cons (get-in db [:session :user-info]) (get-in db [:session :users])))
  ([db by-name]
   (filter #(contains? by-name (:username %)) (get-all-users-info db))))

(re-frame/register-sub
 :active-project
 (fn [db _]
   (let [active-project-name (reaction (get-in @db [:session :active-project :name]))]
     (reaction (get-project-info @db @active-project-name)))))

(re-frame/register-sub
 :active-project-users
 (fn [db _]
   (let [active-project-name (reaction (get-in @db [:session :active-project :name]))
         active-project (reaction (get-project-info @db @active-project-name))
         users (reaction (get-all-users-info @db))
         users-map (reaction (zipmap (map :username @users) @users))]
     (reaction (map #(get @users-map %) (map :username (:users @active-project)))))))

(re-frame/register-sub
 :filtered-users-colors
 (fn [db _]
   (let [filtered-users (reaction (get-in @db [:session :active-project :filtered-users]))
         active-project-name (reaction (get-in @db [:session :active-project :name]))
         active-project (reaction (get-project-info @db active-project-name))
         filtered-users-info (reaction (get-all-users-info @db @filtered-users))]
     (reaction (zipmap (map :username @filtered-users-info)
                       (map #(get-in % [:avatar :dominant-color]) @filtered-users-info))))))
