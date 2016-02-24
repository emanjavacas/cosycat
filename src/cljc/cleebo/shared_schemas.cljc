(ns cleebo.shared-schemas
  (:require [schema.core :as s]))

;{:_id "cpos" :anns [{:ann {"key" "value"} :username "foo" :timestamp 21930198012}]}

(def annotation-schema
  (s/conditional :span {:ann {:span {:IOB s/Str :ann {s/Any s/Any}}}
                        :timestamp s/Int
                        :username s/Str}
                 :else {:ann {s/Any s/Any}
                        :timestamp s/Int
                        :username s/Str}))

(s/defn ^:always-validate ->span-ann :- annotation-schema
  [IOB ann]
  (update ann :ann (fn [ann] {:span {:IOB IOB :ann ann}})))

(s/defn ^:always-validate make-ann :- annotation-schema
  [m username]
  {:ann m
   :username username
   :timestamp #?(:cljs (.now js/Date)
                 :clj  (System/currentTimeMillis))})
