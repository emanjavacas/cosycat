(ns material-ui.core
  (:require-macros [material-ui.core :refer [export-material-ui-react-classes]])
  (:require [reagent.core]
            [taoensso.timbre :as timbre]))

(timbre/debug "Loading material-ui/core.cljs")
(export-material-ui-react-classes)

