(ns cleebo.main
  (:require [cleebo.core :as core]
            [taoensso.timbre :as timbre]))

(timbre/info "Starting cljs in production mode")
;;; init
(core/init!)


