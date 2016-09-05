(ns cleebo.backend.middleware
  (:require [re-frame.core :as re-frame]
            [re-frame.utils :refer [warn]]
            [reagent.core :as reagent]
            [taoensso.timbre :as timbre]
            [cleebo.schemas.app-state-schemas :refer [db-schema]]
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

(defn check-project-exists
  [handler]
  (fn [db [_ {:keys [project-name]} :as args]]
    (let [new-db (handler db args)]
      (if-not project-name
        (do (warn "Project middleware requires named :project-name but got" args) new-db)
        (let [projects (get-in new-db [:projects])]
          (if-not (some #{project-name} (keys projects))
            (do (re-frame/dispatch
                 [:register-session-error
                  {:code "Project not found!"
                   :message "These are not the projects you are looking for."}])
                new-db)
            new-db))))))

(def standard-middleware
  [(when ^boolean goog.DEBUG re-frame/debug)
   (when ^boolean goog.DEBUG (re-frame/after validate-db-schema))])

(def no-debug-middleware
  [(when ^boolean goog.DEBUG)
   (when ^boolean goog.DEBUG (re-frame/after validate-db-schema))])
