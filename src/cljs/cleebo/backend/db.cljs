(ns cleebo.backend.db)

(def default-db
  {:annotation-panel-visibility false
   :session {:query-opts {:corpus "brown-id"
                          :context 5
                          :size 10}
             :query-results {:results nil
                             :query-size nil
                             :query-str nil
                             :status {:status nil :status-text nil}
                             :from nil
                             :to nil}}})
