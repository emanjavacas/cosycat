(ns cosycat.key-bindings
  (:require [keybind.core :as key]
            [re-frame.core :as re-frame]))

(def key-bindings
  {:query-panel [{:key-stroke "shift-n"
                  :event-key :next
                  :fn #(re-frame/dispatch [:query-range :next])}
                 {:key-stroke "shift-p"
                  :event-key :prev
                  :fn #(re-frame/dispatch [:query-range :prev])}
                 {:key-stroke "shift-s"
                  :event-key :swap-panels
                  :fn #(re-frame/dispatch [:swap-panels])}]})

(defn unbind []
  (doseq [{:keys [key-stroke event-key]} (flatten (vals key-bindings))]
    (key/unbind! key-stroke event-key)))

(defn bind [panel-key]
  (doseq [{key-stroke :key-stroke event-key :event-key f :fn} (get key-bindings panel-key)]
    (key/bind! key-stroke event-key f)))

(defn bind-panel-keys [component-key]
  (unbind)
  (bind component-key))
