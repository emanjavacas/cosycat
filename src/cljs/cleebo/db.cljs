(ns cleebo.db)

(def default-db
  {:name "CleeBo"
   :session {:query-opts {:corpus "brown-id"
                          :context 5
                          :size 10}
             :query-results {:results nil
                             :query-size nil
                             :query-str nil
                             :status {:status nil :status-text nil}
                             :from nil
                             :to nil}}})
