(ns cleebo.shared-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            #?(:clj [clojure.core.match :refer [match]]
               :cljs [cljs.core.match :refer-macros [match]])))

;{:_id "cpos" :anns [{:ann {"key" "value"} :username "foo" :timestamp 21930198012}]}

(def span-annotation-schema
  {:span {:IOB s/Str :ann {:key s/Str :value s/Str}}})

(def token-annotation-schema
  {:key s/Str :value s/Any})

(def annotation-schema
  {:ann {:key   s/Str
         :value (s/conditional :IOB {:IOB s/Str} :else s/Str)}
   :timestamp s/Int
   :username s/Str
   (s/optional-key :history)
   [{:ann {:key   s/Str
           :value (s/conditional :IOB {:IOB s/Str} :else s/Str)}
     :timestamp s/Int
     :username s/Str}]})

(defn ws-from-server
  [{:keys [type status data] :as payload}]
  (match [type status]
    [:annotation :ok]    {:status s/Keyword
                          :type   s/Keyword
                          :data   {:hit-id s/Int
                                   :token-id s/Int
                                   :anns [annotation-schema]}}
    [:annotation :error] {:status s/Keyword
                          :type s/Keyword
                          :data {:token-id s/Int
                                 :reason   s/Keyword
                                 (s/optional-key :e) s/Str
                                 (s/optional-key :username) s/Str}}
    [:notify     :ok]    {:status s/Keyword
                          :type    s/Keyword
                          :data {:message s/Str
                                 :by s/Str}}))

(defn ws-from-client
  [{:keys [type data] :as payload}]
  (case type
    :annotation {:type s/Keyword
                 :data {:hit-id s/Int
                        :token-id s/Int
                        :ann annotation-schema}}
    :notify     {:type s/Keyword
                 :data {}}))
