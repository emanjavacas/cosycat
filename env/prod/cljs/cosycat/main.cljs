(ns cosycat.main
  (:require [cosycat.core :as core]
            [taoensso.timbre :as timbre]))

(timbre/info "Starting cljs in production mode")
;;; init
(core/init!)


