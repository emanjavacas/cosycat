(ns cleebo.backend.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]
              [cleebo.utils :refer [filter-marked-hits]]
              [cleebo.app-utils :refer [select-values]]
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
 :has-error?
 (fn [db [_ component-id]]
   (reaction (get-in @db [:session :has-error? component-id]))))

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

(re-frame/register-sub                  ;related to query results
 :project-session                       ;TODO: no-project queries
 (fn [db [_ & path]]
   (let [active-project (reaction (get @db [:session :active-project]))
         project (reaction (get-in @db [:projects @active-project]))]
     (reaction (get-in @project (into [:session] path))))))

(re-frame/register-sub
 :settings
 (fn [db [_ & path]]
   (let [global-settings (reaction (:settings @db))]
     (reaction (get-in @global-settings path)))))

(re-frame/register-sub
 :corpora
 (fn [db _]
   (reaction (:corpora @db))))

(re-frame/register-sub
 :has-query?
 (fn [db _]
   (let [active-project (reaction (get-in @db [:session :active-project]))
         project (reaction (get-in @db [:projects @active-project]))]
     (reaction (not (zero? (get-in @project [:session :results-summary :query-size])))))))

(re-frame/register-sub
 :results
 (fn [db _]
   (let [active-project (reaction (get-in @db [:session :active-project]))
         project (reaction (get-in @db [:projects @active-project]))
         results-ids (reaction (get-in @project [:session :results]))
         results-by-id (reaction (get-in @project [:session :results-by-id]))]
     (reaction (select-values @results-by-id @results-ids)))))

(re-frame/register-sub ;all marked hits, also if currently not in table-results
 :marked-hits
 (fn [db [_ {:keys [has-marked?]}]]
   (let [active-project (reaction (get-in @db [:session :active-project]))
         project (reaction (get-in @db [:projects @active-project]))
         results-by-id (reaction (get-in @project [:session :results-by-id]))]
     (reaction (vals (filter-marked-hits @results-by-id :has-marked? has-marked?))))))

(re-frame/register-sub
 :marked-tokens
 (fn [db _]
   (let [active-project (reaction (get-in @db [:session :active-project]))
         project (reaction (get-in @db [:projects @active-project]))
         results-by-id (reaction (get-in @project [:session :results-by-id]))]
     (reaction (mapcat (fn [[_ {:keys [hit id meta]}]]
                         (->> hit
                              (filter :marked)
                              (map #(assoc % :hit-id id))))
                       @results-by-id)))))

(defn get-users
  ([db] (cons (:me db) (map :user (:users db))))
  ([db by-name] (filter #(contains? by-name (:name %)) (get-users db))))

(defn get-user [db username]
  (first (get-users db #{username})))

(re-frame/register-sub
 :users
 (fn [db _] (reaction (get-users @db))))

(re-frame/register-sub
 :user
 (fn [db [_ username & [path]]]
   (let [user (reaction (get-user @db username))]
     (if path
       (reaction (get-in user path))
       user))))

(re-frame/register-sub
 :active-project
 (fn [db [_ & path]]
   (let [active-project-name (reaction (get-in @db [:session :active-project]))]
     (reaction (get-in @db (into [:projects @active-project-name] path))))))

(re-frame/register-sub                  ;{username user-map) for each user in project
 :active-project-users
 (fn [db _]
   (let [active-project-name (reaction (get-in @db [:session :active-project]))
         active-project (get-in @db [:projects @active-project-name])
         users (reaction (get-users @db))
         users-map (reaction (zipmap (map :name @users) @users))]
     (reaction (map #(get @users-map %) (map :username (:users @active-project)))))))

(re-frame/register-sub
 :filtered-users-colors
 (fn [db _]
   (let [active-project-name (reaction (get-in @db [:session :active-project]))
         active-project (get-in @db [:projects @active-project-name])
         filtered-users (reaction (get-in @active-project [:session :filtered-users]))
         filtered-users-info (reaction (get-users @db @filtered-users))]
     (reaction (zipmap (map :username @filtered-users-info)
                       (map #(get-in % [:avatar :dominant-color]) @filtered-users-info))))))

(re-frame/register-sub
 :read-history
 (fn [db [_ path & {:keys [filter-fn] :or {filter-fn identity}}]]
   (let [ws-history (reaction (get-in @db (into [:history] path)))]
     (reaction (filter filter-fn @ws-history)))))
