(ns cosycat.viewport
  (:import [goog.dom ViewportSizeMonitor])
  (:require [reagent.core :as reagent]
            [goog.dom :as gdom]
            [goog.events :as goog-events]
            [goog.events.EventType :as event-type]))

(defn get-viewport-props []
  (let [viewport-size (gdom/getViewportSize)
        width (.-width viewport-size)
        height (.-height viewport-size)]
    {:width width :height height}))

(def viewport (reagent/atom (get-viewport-props)))

(let [viewport-monitor (ViewportSizeMonitor.)]
  (goog-events/listen viewport-monitor
                      event-type/RESIZE
                      (fn [e]
                        (reset! viewport (get-viewport-props)))))
