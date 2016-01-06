(ns cleebo.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]))

(re-frame/register-sub
 :name
 (fn [db _]
   (reaction (:name @db))))

(re-frame/register-sub
 :active-panel
 (fn [db _]
   (reaction (:active-panel @db))))

(re-frame/register-sub
 :results
 (fn [db _]
   (reaction (:results @db))))

(re-frame/register-sub
 :msgs
 (fn [db _]
   (reaction (:msgs @db))))
