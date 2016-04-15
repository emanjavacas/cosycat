(ns cleebo.main
  (:require [figwheel.client :as figwheel]
            [cleebo.core :refer [init]]
            [cleebo.env :refer [cljs-env]]
            [taoensso.timbre :as timbre])
  (:require-macros [cleebo.env :refer [cljs-env]]))

(timbre/info "Starting cljs in production mode")
;;; init
(init)


