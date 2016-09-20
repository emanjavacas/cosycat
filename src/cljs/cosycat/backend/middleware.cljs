(ns cosycat.backend.middleware
  (:require [re-frame.core :as re-frame]
            [re-frame.utils :refer [warn]]
            [reagent.core :as reagent]
            [taoensso.timbre :as timbre]
            [cosycat.schemas.app-state-schemas :refer [db-schema]]
            [cosycat.routes :refer [refresh]]
            [schema.core :as s :include-macros true]))

(enable-console-print!)

(defn log-ex
  "print whole stacktrace before being sucked by async channel"
  [handler]
  (fn [db v]
    (try
      (handler db v)
      (catch :default e
        (do (.error js/console e.stack)
            (throw e))))))

(defn validate-db-schema
  [db]
  (if-let [res (s/check db-schema db)]
    (do (timbre/debug "validation error:" res)
        (.log js/console "validation error: " res))))

(defn project-not-found-error [project-name]
  (re-frame/dispatch
   [:register-session-error
    {:code (str "Project " project-name " not found!")
     :message "These are not the projects you are looking for."}]))

(defn check-project-exists
  [handler]
  (fn [db [_ {:keys [project-name]} :as args]]
    (let [new-db (handler db args)]
      (if-not project-name
        (do (warn "Project middleware requires named :project-name but got" args) new-db)
        (if-let [projects (get-in new-db [:projects])]
          (if-not (some #{project-name} (keys projects))
            (do (project-not-found-error project-name) new-db)
            new-db)
          (do (re-frame/dispatch [:initialize-session])
              (refresh)
              new-db))))))

(def standard-middleware
  [(when ^boolean goog.DEBUG re-frame/debug)
   (when ^boolean goog.DEBUG (re-frame/after validate-db-schema))])

(def no-debug-middleware
  [(when ^boolean goog.DEBUG)
   (when ^boolean goog.DEBUG (re-frame/after validate-db-schema))])
