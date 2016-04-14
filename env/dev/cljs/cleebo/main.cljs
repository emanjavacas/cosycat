(ns ^:figwheel-no-load cleebo.main
  (:require [figwheel.client :as figwheel]
            [cleebo.core :refer [init mount-root]])
  (:require-macros [cleebo.env :refer [cljs-env]]))

;; start figwheel server
(figwheel/start
 {:websocket-url (str "ws://" (cljs-env :host) ":3449/figwheel-ws")
  :js-load-callback mount-root})

;;; init
(.log js/console "Reloading!")
(init)


