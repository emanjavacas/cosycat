(ns cleebo.settings.components.corpus-settings
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cleebo.components :refer [dropdown-select]]
            [cleebo.settings.components.shared-components :refer [row-component]]
            [taoensso.timbre :as timbre]))

(def help-map
  {:corpora "Display installed corpora"})

(defn corpus-controller []
  (let [help-text (reagent/atom "")]
    (fn []
      [row-component
       :label "Installed corpora"
       :controllers [bs/button
                     {:onClick #(timbre/debug "Hello")}
                     "Show corpora"]
       :help-text help-text])))
