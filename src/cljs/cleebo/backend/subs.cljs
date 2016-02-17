(ns cleebo.backend.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]
              [cleebo.utils :refer [filter-marked]]
              [taoensso.timbre :as timbre]))

(re-frame/register-sub
 :name
 (fn [db _]
   (reaction (:name @db))))

(re-frame/register-sub
 :active-panel
 (fn [db _]
   (reaction (:active-panel @db))))

(re-frame/register-sub
 :notifications
 (fn [db _]
   (let [notifications (reaction (:notifications @db))]
     (reaction (reverse (sort-by (fn [[_ {date :date}]] date) @notifications))))))

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
 :query-opts
 (fn [db _]
   (reaction (get-in @db [:session :query-opts]))))

(re-frame/register-sub
 :msgs
 (fn [db _]
   (reaction (:msgs @db))))

(re-frame/register-sub
 :query-results
 (fn [db _]
   (reaction (get-in @db [:session :query-results]))))

(re-frame/register-sub
 :results
 (fn [db _]
   (let [results (reaction (get-in @db [:session :results]))
         query-results (reaction (get-in @db [:session :query-results]))
         from (reaction (:from @query-results))
         to (reaction (:to @query-results))]
     (reaction (select-keys @results (range @from @to))))))

(re-frame/register-sub
 :marked-hits
 (fn [db _]
   (let [results (reaction (get-in @db [:session :results]))]
     (reaction (filter-marked @results)))))
