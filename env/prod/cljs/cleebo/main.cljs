(ns cleebo.main
  (:require [figwheel.client :as figwheel]
            [cleebo.core :refer [init]]
            [cleebo.env :refer [cljs-env]])
  (:require-macros [cleebo.env :refer [cljs-env]]))

;;; init
(init)


