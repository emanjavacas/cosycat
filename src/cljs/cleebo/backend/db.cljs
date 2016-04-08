(ns cleebo.backend.db
  (:require-macros [cleebo.env :refer [cljs-env]]))

(def default-db
  "defines app default state"
  {:settings {:notifications {:delay 7500}
              :snippet-size 25}
   :session {:active-panel :query-panel
             :notifications {}
             :query-opts {:corpus (first (cljs-env :blacklab :corpora))
                          :context 5
                          :size 10
                          :criterion "match"
                          :prop-name "word"}
             :query-results {:query-size 0
                             :query-str ""
                             :status {:status :ok :status-content ""}
                             :from 0
                             :to 0}
             :results-by-id {}
             :results []}})
