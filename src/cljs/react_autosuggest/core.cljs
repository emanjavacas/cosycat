(ns react-autosuggest.core
  (:require [reagent.core :as reagent]
            [cljsjs.react-autosuggest]))

(def autosuggest (reagent/adapt-react-class js/Autosuggest))
