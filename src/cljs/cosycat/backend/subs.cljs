(ns cosycat.backend.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]
              [cosycat.utils :refer [filter-marked-hits]]
              [cosycat.app-utils :refer [select-values]]
              [taoensso.timbre :as timbre]))

(re-frame/register-sub
 :db
 (fn [db _]
   (reaction @db)))

(defn filter-tagsets [selected-tagsets tagsets]
  (if-not selected-tagsets
    tagsets
    (filter #(some (hash-set (:name %)) selected-tagsets) tagsets)))

(re-frame/register-sub
 :selected-tagsets
 (fn [db [_ & path]]
   (let [tagsets (reaction (:tagsets @db))
         active-project (reaction (get-in @db [:session :active-project]))
         selected-tagsets (reaction (get-in @db [:projects @active-project :settings :tagsets]))]
     (reaction (mapv #(get-in % path) (filter-tagsets @selected-tagsets @tagsets))))))

(re-frame/register-sub
 :tagsets
 (fn [db _]
   (reaction (:tagsets @db))))

(re-frame/register-sub
 :modals
 (fn [db [_ modal]]
   (reaction (get-in @db [:session :modals modal]))))

(re-frame/register-sub
 :snippet-hit
 (fn [db _]
   (let [snippet-modal (reaction (get-in @db [:session :modals :snippet]))]
     (reaction (when-let [{:keys [hit-id]} @snippet-modal]
                 (let [active-project (reaction (get-in @db [:session :active-project]))
                       project (reaction (get-in @db [:projects @active-project]))]
                   (get-in @project [:session :query :results-by-id hit-id])))))))

(re-frame/register-sub
 :session-has-error?
 (fn [db _]
   (reaction (get-in @db [:session :session-error]))))

(re-frame/register-sub
 :component-has-error?
 (fn [db [_ component-id]]
   (reaction (get-in @db [:session :component-error component-id]))))

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
   (let [active-project (reaction (get-in @db [:session :active-project]))
         project (reaction (get-in @db [:projects @active-project]))]
     (reaction (get-in @project (into [:session] path))))))

(re-frame/register-sub
 :settings
 (fn [db [_ & path]]
   (let [global-settings (reaction (get-in @db [:settings]))]
     (reaction (get-in @global-settings path)))))

(re-frame/register-sub
 :corpora
 (fn [db [_ & path]]
   (reaction (doall (mapv #(get-in % path) (:corpora @db))))))

(re-frame/register-sub
 :corpus-config
 (fn [db [_ & path]]
   (let [corpora (reaction (:corpora @db))
         corpus-name (reaction (get-in @db [:settings :query :corpus]))
         corpus (reaction (some #(when (= @corpus-name (:corpus %)) %) @corpora))]
     (reaction (get-in @corpus path)))))

(re-frame/register-sub
 :projects
 (fn [db _]
   (reaction (:projects @db))))

(re-frame/register-sub
 :me
 (fn [db [_ & path]]
   (reaction (get-in @db (into [:me] path)))))

(re-frame/register-sub
 :has-query?
 (fn [db _]
   (let [active-project (reaction (get-in @db [:session :active-project]))
         project (reaction (get-in @db [:projects @active-project]))
         results-summary (reaction (get-in @project [:session :query :results-summary]))]
     (reaction (not (empty? @results-summary))))))

(re-frame/register-sub
 :has-query-results?
 (fn [db _]
   (let [active-project (reaction (get-in @db [:session :active-project]))
         project (reaction (get-in @db [:projects @active-project]))
         size (reaction (get-in @project [:session :query :results-summary :query-size]))]
     (reaction (and (number? @size) (not (zero? @size)))))))

(re-frame/register-sub
 :results
 (fn [db [_ & [hit-ids]]]
   (let [active-project (reaction (get-in @db [:session :active-project]))
         project (reaction (get-in @db [:projects @active-project]))
         results-by-id (reaction (get-in @project [:session :query :results-by-id]))]
     (if (empty? hit-ids)
       (reaction (select-values @results-by-id (get-in @project [:session :query :results])))
       (reaction (select-values @results-by-id hit-ids))))))

(re-frame/register-sub ;all marked hits, also if currently not in table-results
 :marked-hits
 (fn [db [_ {:keys [has-marked?]}]]
   (let [active-project (reaction (get-in @db [:session :active-project]))
         project (reaction (get-in @db [:projects @active-project]))
         results-by-id (reaction (get-in @project [:session :query :results-by-id]))]
     (reaction (vals (filter-marked-hits @results-by-id :has-marked? has-marked?))))))

(re-frame/register-sub
 :marked-tokens
 (fn [db _]
   (let [active-project (reaction (get-in @db [:session :active-project]))
         project (reaction (get-in @db [:projects @active-project]))
         results-by-id (reaction (get-in @project [:session :query :results-by-id]))]
     (reaction (mapcat (fn [{:keys [hit id meta]}]
                         (->> hit (filter :marked) (map #(assoc % :hit-id id))))
                       (vals @results-by-id))))))

(defn get-users
  ([db] (cons (:me db) (map :user (:users db))))
  ([db by-name] (filter #(contains? by-name (:username %)) (get-users db))))

(defn get-user [db username]
  (first (get-users db #{username})))

(re-frame/register-sub
 :users
 (fn [db [_ & {:keys [exclude-me] :or {exclude-me false}}]]
   (let [users (reaction (get-users @db))]
     (if exclude-me
       (reaction (remove #(= (:username %) (get-in @db [:me :username])) @users))
       users))))

(re-frame/register-sub
 :user
 (fn [db [_ username & path]]
   (let [user (reaction (get-user @db username))]
     (if path
       (reaction (get-in @user path))
       user))))

(re-frame/register-sub
 :active-project
 (fn [db [_ & path]]
   (let [active-project-name (reaction (get-in @db [:session :active-project]))]
     (reaction (get-in @db (into [:projects @active-project-name] path))))))

(re-frame/register-sub
 :active-project-role
 (fn [db _]
   (let [active-project-name (reaction (get-in @db [:session :active-project]))
         users (reaction (get-in @db [:projects @active-project-name :users]))
         me (reaction (get-in @db [:me :username]))]
     (reaction (->> @users (filter #(= @me (:username %))) first :role)))))

(re-frame/register-sub                  ;{username user-map) for each user in project
 :active-project-users
 (fn [db _]
   (let [active-project (reaction (get-in @db [:session :active-project]))
         project (reaction (get-in @db [:projects @active-project]))
         users (reaction (get-users @db))
         users-map (reaction (zipmap (map :username @users) @users))]
     (reaction (map #(get @users-map %) (map :username (:users @project)))))))

(re-frame/register-sub
 :filtered-users-colors
 (fn [db _]
   (let [active-project-name (reaction (get-in @db [:session :active-project]))
         active-project (reaction (get-in @db [:projects @active-project-name]))
         filtered-users (reaction (get-in @active-project [:session :filtered-users]))
         filtered-users-info (reaction (get-users @db @filtered-users))]
     (reaction (zipmap (map :username @filtered-users-info)
                       (map #(get-in % [:avatar :dominant-color]) @filtered-users-info))))))

(re-frame/register-sub
 :events
 (fn [db _]
   (let [active-project (reaction (get-in @db [:session :active-project]))]
     (reaction (vals (get-in @db [:projects @active-project :events])))
     ;; TODO: if not in project it should return app-wide events
     )))

(re-frame/register-sub
 :project-queries
 (fn [db [_ & {:keys [filter-corpus] :or {filter-corpus true}}]]
   (let [active-project (reaction (get-in @db [:session :active-project]))
         corpus (reaction (get-in @db [:settings :query :corpus]))]
     (reaction (cond->> (vals (get-in @db [:projects @active-project :queries]))
                 filter-corpus (filter #(= @corpus (get-in % [:query-data :corpus]))))))))

(re-frame/register-sub
 :discarded-hits
 (fn [db [_ query-id]]
   (let [active-project (reaction (get-in @db [:session :active-project]))
         discarded (reaction (get-in @db [:projects @active-project :queries query-id :discarded]))]
     (reaction (into (hash-set) (map :hit @discarded))))))
