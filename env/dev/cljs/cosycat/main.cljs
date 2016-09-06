(ns ^:figwheel-no-load cosycat.main
  (:require [figwheel.client :as figwheel :include-macros true]
            [cosycat.core :as core]
            [taoensso.timbre :as timbre]
            [devtools.core :as devtools])
  (:require-macros [cosycat.env :refer [cljs-env]]))

(timbre/info "Starting cljs in development mode")

;; init devtools
(devtools/enable-feature! :sanity-hints :dirac)
(devtools/install!)
;; start figwheel server
(figwheel/watch-and-reload
 :websocket-url (str "ws://" (cljs-env :host) ":3449/figwheel-ws")
 :jsload-callback core/mount-root)

;;; init
(core/init!)


