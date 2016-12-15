(ns react-date-range.core
  (:require [reagent.core :as reagent]
            [cljsjs.react-date-range]))

(def calendar (reagent/adapt-react-class js/ReactDateRange.Calendar))

(def date-range (reagent/adapt-react-class js/ReactDateRange.DateRange))
