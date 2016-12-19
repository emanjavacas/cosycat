(ns cosycat.backend.handlers.components
  (:require [re-frame.core :as re-frame]
            [cosycat.backend.middleware
             :refer [standard-middleware no-debug-middleware check-project-exists]]
            [cosycat.utils :refer [time-id has-marked? update-token]]
            [cosycat.app-utils :refer [deep-merge disjconj]]
            [cosycat.backend.db :refer [get-project-settings]]
            [taoensso.timbre :as timbre]))

(re-frame/register-handler
 :remove-active-project
 standard-middleware
 (fn [db _]
   (assoc-in db [:session :active-project] nil)))

(re-frame/register-handler
 :set-active-project
 (conj standard-middleware check-project-exists)
 (fn [db [_ {:keys [project-name]}]]
   (let [project-settings (get-project-settings db project-name)]
     (-> db
         (assoc-in [:session :active-project] project-name)
         (update :settings deep-merge project-settings)))))

(re-frame/register-handler
 :open-modal
 standard-middleware
 (fn [db [_ modal & [data]]]
   (if-not data
     (assoc-in db [:session :modals modal] true)     
     (update-in db [:session :modals modal] deep-merge data))))

(re-frame/register-handler
 :close-modal
 standard-middleware
 (fn [db [_ modal]]
   (assoc-in db [:session :modals modal] false)))

(re-frame/register-handler
 :start-throbbing
 standard-middleware
 (fn [db [_ panel]]
   (assoc-in db [:session :throbbing? panel] true)))

(re-frame/register-handler
 :stop-throbbing
 standard-middleware
 (fn [db [_ panel]]
   (assoc-in db [:session :throbbing? panel] false)))

(re-frame/register-handler
 :register-error
 standard-middleware
 (fn [db [_ component-id data]]
   (assoc-in db [:session :component-error component-id] data)))

(re-frame/register-handler
 :drop-error
 standard-middleware
 (fn [db [_ component-id]]
   (update-in db [:session :component-error] dissoc component-id)))

(re-frame/register-handler
 :panel-open
 standard-middleware
 (fn [db [_ id v]]
   (let [active-project (get-in db [:session :active-project])
         path [:projects active-project :session :components :panel-open id]]
     (assoc-in db path v))))

(re-frame/register-handler
 :swap-panels
 standard-middleware
 (fn [db _]
   (let [active-project (get-in db [:session :active-project])
         path [:projects active-project :session :components]
         is-open (get-in db (into path [:panel-open :query-frame]))
         should-open (if is-open :annotation-frame :query-frame)
         other-panel (if (= should-open :query-frame) :annotation-frame :query-frame)]
     (-> db
         (assoc-in (into path [:panel-open should-open]) true)
         (assoc-in (into path [:panel-open other-panel]) false)))))

(re-frame/register-handler
 :open-hit
 standard-middleware
 (fn [db [_ hit-id]]
   (let [active-project (get-in db [:session :active-project])
         path [:projects active-project :session :components :open-hits]]
     (update-in db path disjconj hit-id))))

(re-frame/register-handler
 :close-hits
 standard-middleware
 (fn [db _]
   (let [active-project (get-in db [:session :active-project])]
     (assoc-in db [:projects active-project :session :components :open-hits] #{}))))

(re-frame/register-handler
 :open-hits
 standard-middleware
 (fn [db _]
   (let [active-project (get-in db [:session :active-project])
         results (get-in db [:projects active-project :session :query :results :results-by-id])
         marked-hits (filter #(get-in % [:meta :marked]) (vals results))]
     (assoc-in db [:projects active-project :session :components :open-hits]
               (apply hash-set (map :id marked-hits))))))

(re-frame/register-handler
 :set-token-field
 (fn [db [_ token-field]]               ;todo, should validate token-field
   (let [active-project (get-in db [:session :active-project])
         path [:projects active-project :session :components :token-field]]
     (assoc-in db path token-field))))

(re-frame/register-handler
 :set-active-query
 standard-middleware
 (fn [db [_ id]]
   (let [active-project (get-in db [:session :active-project])]
     (assoc-in db [:projects active-project :session :components :active-query] id))))

(re-frame/register-handler
 :unset-active-query
 standard-middleware
 (fn [db _]
   (let [active-project (get-in db [:session :active-project])]
     (update-in db [:projects active-project :session :components] dissoc :active-query))))

(re-frame/register-handler
 :set-active-project-frame
 standard-middleware
 (fn [db [_ frame]]
   (let [active-project (get-in db [:session :active-project])]
     (assoc-in db [:projects active-project :session :components :active-project-frame] frame))))

(re-frame/register-handler
 :set-project-session-component
 standard-middleware
 (fn [db [_ path value]]
   (let [active-project (get-in db [:session :active-project])]
     (assoc-in db (into [:projects active-project :session :components] path) value))))

(re-frame/register-handler
 :unset-project-session-component
 standard-middleware
 (fn [db [_ path key]]
   (let [active-project (get-in db [:session :active-project])]
     (update-in db (into [:projects active-project :session :components] path) dissoc key))))

(re-frame/register-handler
 :toggle-project-session-component
 standard-middleware
 (fn [db [_ path]]
   (let [active-project (get-in db [:session :active-project])
         path (into [:projects active-project :session :components] path)]
     (if (get-in db path)
       (assoc-in db path false)
       (assoc-in db path true)))))

;;; marking
(re-frame/register-handler
 :mark-hit
 standard-middleware
 (fn [db [_ {:keys [hit-id flag]}]]
   (let [active-project (get-in db [:session :active-project])
         path [:projects active-project :session :query :results :results-by-id hit-id :meta :marked]]
     (assoc-in db path (boolean flag)))))

(re-frame/register-handler
 :mark-hits
 standard-middleware
 (fn [db _]
   (let [project-name (get-in db [:session :active-project])
         query-id (get-in db [:projects project-name :session :components :active-query])
         {query-hits :hits} (get-in db [:projects project-name :queries query-id])
         path-to-results [:projects project-name :session :query :results :results-by-id]]
     (reduce (fn [acc hit-id]
               (let [status (get-in query-hits [hit-id :status])]
                 (if-not (= status "discarded")
                   (assoc-in acc (into path-to-results [hit-id :meta :marked]) true)
                   acc)))
             db
             (get-in db [:projects project-name :session :query :results :results])))))

(re-frame/register-handler
 :unmark-hits
 standard-middleware
 (fn [db _]
   (let [project-name (get-in db [:session :active-project])
         path-to-results [:projects project-name :session :query :results :results-by-id]]
     (reduce (fn [acc hit-id]
               (assoc-in acc (into path-to-results [hit-id :meta :marked]) false))
             db
             (keys (get-in db path-to-results))))))

(defn mark-token [token] (assoc token :marked true))

(defn unmark-token [token] (dissoc token :marked))

(re-frame/register-handler
 :mark-token
 standard-middleware
 (fn [db [_ {:keys [hit-id token-id]}]]
   (let [active-project (get-in db [:session :active-project])
         path [:projects active-project :session :query :results :results-by-id hit-id]
         hit (get-in db path)]
     (assoc-in db path (update-token hit token-id mark-token)))))

(re-frame/register-handler
 :unmark-token
 standard-middleware
 (fn [db [_ {:keys [hit-id token-id]}]]
   (let [active-project (get-in db [:session :active-project])
         hit-map (get-in db [:projects active-project :session :query :results :results-by-id hit-id])
         hit-map (assoc-in hit-map [:meta :has-marked] (boolean (has-marked? hit-map token-id)))
         path [:projects active-project :session :query :results :results-by-id hit-id]]
     (assoc-in db path (update-token hit-map token-id unmark-token)))))
