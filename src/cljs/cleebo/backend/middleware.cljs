(ns cleebo.backend.middleware
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [taoensso.timbre :as timbre]
            [schema.core :as s :include-macros true]
            [schema.spec.core :as spec]
            [schema.spec.collection :as coll]))

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

(def token-hit-schema
  {;; required keys
   (s/required-key :word)   s/Str
   (s/required-key :id)     s/Any
   ;; optional keys
   (s/optional-key :marked) s/Bool
   (s/optional-key :ann)    s/Any
   ;; any other additional keys
   s/Keyword                s/Any})

(def token-meta-schema
  {;; optional keys
   (s/optional-key :marked) s/Bool
   (s/optional-key :ann)    s/Any
   ;; any other additional keys
   s/Keyword                s/Any})

(def results-schema
  {s/Int {:hit  [token-hit-schema]
          :meta token-meta-schema}})

(def query-opts-schema
  {:corpus s/Str
   :context s/Int
   :size s/Int})

(def query-results-schema
  {:query-size s/Int;(s/conditional nil? nil :else s/Int)
   :query-str  s/Str;(s/conditional nil? nil :else s/Str)
   :from       s/Int
   :to         s/Int
   :status {:status         s/Keyword;(s/conditional nil? nil :else s/Keyword)
            :status-content s/Str;(s/conditional nil? nil :else s/Str)
            }})

(def db-schema
  {:active-panel s/Keyword
   :throbbing? {s/Keyword s/Bool}
   :session {:query-opts query-opts-schema
             :query-results query-results-schema
             :results (s/conditional empty? {} :else results-schema)}})

(defn validate-db-schema
  [db]
  (let [res (s/check db-schema db)]
    (when (some? res)
      (.log js/console "validation error: " res))))

(def standard-middleware
  [(when ^boolean goog.DEBUG log-ex)
   (when ^boolean goog.DEBUG re-frame/debug)
   (when ^boolean goog.DEBUG (re-frame/after validate-db-schema))])

(def no-debug-middleware
  [(when ^boolean goog.DEBUG log-ex)
   (when ^boolean goog.DEBUG (re-frame/after validate-db-schema))])
