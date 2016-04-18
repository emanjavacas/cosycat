(ns cleebo.backend.middleware
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [taoensso.timbre :as timbre]
            [cleebo.schemas.app-state-schemas :refer [db-schema]]
            [schema.core :as s :include-macros true]))

(enable-console-print!)

(defn log-ex
  "print whole stacktrace before being sucked by asyn channel"
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

(def standard-middleware
  [(when ^boolean goog.DEBUG log-ex)
   (when ^boolean goog.DEBUG re-frame/debug)
   (when ^boolean goog.DEBUG (re-frame/after validate-db-schema))])

(def no-debug-middleware
  [(when ^boolean goog.DEBUG log-ex)
   (when ^boolean goog.DEBUG (re-frame/after validate-db-schema))])
