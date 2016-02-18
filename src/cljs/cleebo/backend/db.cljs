(ns cleebo.backend.db)

(def default-db
  "defines app default state"
  {:active-panel :query-panel
   :session {:query-opts {:corpus "brown-id"
                          :context 5
                          :size 10}
             :query-results {:query-size nil
                             :query-str nil
                             :status {:status nil :status-text nil}
                             :from 0
                             :to 0}
             :results {}}})



