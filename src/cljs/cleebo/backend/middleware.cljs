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

;; (defn debug
;;   "Middleware which logs debug information for each event.
;;   Includes a clojure.data/diff of the db, before vs after, showing the changes
;;   caused by the event handler.
;;   See also: https://gist.github.com/mike-thompson-day8/9439d8c502c2f307c029a142b689852d
;;   "
;;   [handler]
;;   (fn debug-handler
;;     [db v]
;;     (.log js/console "Handling re-frame event: " v)
;;     (let [new-db  (handler db v)
;;           [before after] [new-db new-db]; (clojure.data/diff db new-db)
;;           db-changed? (or (some? before) (some? after))]
;;       (if db-changed?
;;         (do (.log js/console "clojure.data/diff for: " v)
;;             (.log js/console "only before: " before)
;;             (.log js/console "only after : " after))
;;         (.log js/console "clojure.data/diff no changes for: " v))
;;       new-db)))

(def standard-middleware
  ;; []
  [(when ^boolean goog.DEBUG)
   (when ^boolean goog.DEBUG re-frame/debug)
   ;; (when ^boolean goog.DEBUG debug)
   (when ^boolean goog.DEBUG (re-frame/after validate-db-schema))])

(def no-debug-middleware
  [(when ^boolean goog.DEBUG)
   (when ^boolean goog.DEBUG (re-frame/after validate-db-schema))])
