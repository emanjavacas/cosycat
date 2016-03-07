(ns cleebo.autocomplete
  (:require  [goog.userAgent :as ua]
             [goog.events :as events]
             [goog.events.EventType]
             [clojure.string :as string]
             [cljs.core.async :refer [>! <! alts! chan sliding-buffer put!]]
             
             ;; [blog.responsive.core :as resp]
             ;; [blog.utils.dom :as dom]
             ;; [blog.utils.helpers :as h]
             ;; [blog.utils.reactive :as r]
             )
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defprotocol ISelectable
  (-select! [list n])
  (-unselect! [list n]))

(defn selector [in list data]
  (let [out (chan)]
    (go (loop [highlighted ::none selected ::none]
          (let [e (<! in)]
            (if (= e :select)
              (do
                (when (number? selected)
                  (-unselect! list selected))
                (-select! list highlighted)
                (>! out [:select (nth data highlighted)])
                (recur highlighted highlighted))
              (do
                (>! out e)
                (if (or (= e ::none) (number? e))
                  (recur e selected)
                  (recur highlighted selected)))))))
    out))

(defprotocol IHideable
  (-hide! [view])
  (-show! [view]))

(defprotocol ITextField
  (-set-text! [field text])
  (-text [field]))

(defprotocol IUIList
  (-set-items! [list items]))
