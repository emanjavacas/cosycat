(ns cleebo.shared-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            #?(:clj [clojure.core.match :refer [match]]
               :cljs [cljs.core.match :refer-macros [match]])))

;{:_id "cpos" :anns [{:ann {"key" "value"} :username "foo" :timestamp 21930198012}]}

(def span-annotation-schema
  {:ann {:span {:IOB s/Str :ann {:key s/Str :value s/Str}}}
   :timestamp s/Int
   :username s/Str})

(def token-annotation-schema
  {:ann {:key s/Str :value s/Any}
   :timestamp s/Int
   :username s/Str})

(def annotation-schema
  (s/conditional #(get-in % [:ann :span]) span-annotation-schema
                 :else                    token-annotation-schema))

(defn ws-from-server
  [{:keys [type status data] :as payload}]
  (match [type status]
    [:annotation :ok]    {:status :ok
                          :type   :annotation
                          :data   {:hit-id s/Int
                                   :token-id s/Int
                                   :ann annotation-schema}}
    [:annotation :error] {:status :error
                          :type :annotation
                          :data {:token-id s/Int
                                 :reason   s/Keyword
                                 (s/optional-key :e) s/Str
                                 (s/optional-key :username) s/Str}}
    [:notify     :ok]    {:by      s/Str
                          :type    s/Keyword
                          :message s/Str}))
