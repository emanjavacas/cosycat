(ns ^:figwheel-no-load cleebo.main
  (:require [figwheel.client :as figwheel :include-macros true]
            [cleebo.core :as core])
  (:require-macros [cleebo.env :refer [cljs-env]]))

;; start figwheel server
(figwheel/watch-and-reload
 :websocket-url (str "ws://" (cljs-env :host) ":3449/figwheel-ws")
 :jsload-callback core/mount-root)

;;; init
(core/init!)


