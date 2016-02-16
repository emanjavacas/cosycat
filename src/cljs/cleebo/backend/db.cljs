(ns cleebo.backend.db)

(def default-db
  {:annotation-panel-visibility false
   :selection {:tokens {}
               :lines  {}}
   :session {:query-opts {:corpus "brown-id"
                          :context 5
                          :size 10}
             :results {}
             :query-results {:query-size nil
                             :query-str nil
                             :status {:status nil :status-text nil}
                             :from 0
                             :to 0}}})
