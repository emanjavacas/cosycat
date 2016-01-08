(ns cleebo.db)

(def default-db
  {:name "CleeBo"
   :session {:query-opts {:corpus "PYCCLE-ECCO"
                          :context 5
                          :size 10}
             :query-results {:results nil
                             :query-size nil
                             :query-str nil
                             :status nil
                             :from nil
                             :to nil}}})
