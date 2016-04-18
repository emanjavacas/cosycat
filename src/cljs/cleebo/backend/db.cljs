(ns cleebo.backend.db)

(def default-db
  "defines app default state"
  {:settings {:notifications {:delay 7500}
              :snippets {:snippet-size 25
                         :snippet-delta 10}}
   :session {:active-panel :front-panel
             :query-opts {:corpus ""
                          :context 5
                          :size 10
                          :criterion "match"
                          :prop-name "word"}
             :query-results {:query-size 0
                             :query-str ""
                             :status {:status :ok :status-content ""}
                             :from 0
                             :to 0}
             :notifications {}
             :results-by-id {}
             :results []}})
