(ns cleebo.shared-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            #?(:clj [clojure.core.match :refer [match]]
               :cljs [cljs.core.match :refer-macros [match]])))

(def cpos-schema s/Int)

(def history-schema
  [{:ann {:key s/Str
          :value s/Str}
    :username s/Str
    :timestamp s/Int}])

(def token-span-schema
  {:type (s/enum "token")
   :scope cpos-schema})

(def iob-span-schema
  {:type (s/enum "IOB")
   :scope {:span {:B cpos-schema
                  :O cpos-schema}}})

(def annotation-schema
  {:ann {:key s/Str
         :value s/Str}
   :username s/Str
   :timestamp s/Int
   :span token-span-schema
   (s/optional-key :history) history-schema})

(def cpos-ann-schema
  {:anns [{:key s/Str :ann-id s/Int}]
   (s/optional-key :_id) cpos-schema})

(def ann-ok-from-server-schema
  "multiple anns implies multiple hit-ids"
  {:status s/Keyword
   :type   s/Keyword
   :data   {:hit-id   (s/if vector? [s/Int] s/Int)
            :ann      (s/if vector? [annotation-schema] annotation-schema)}
   :payload-id s/Any})

(def ann-error-from-server-schema
  {:status s/Keyword                    ;todo
   :type s/Keyword
   :data {:scope (s/if vector? [s/Int] s/Int) ;todo
          :reason   s/Keyword
          (s/optional-key :e) s/Str
          (s/optional-key :username) s/Str}
   :payload-id s/Any})

(defn ws-from-server
  [{:keys [type status data] :as payload}]
  (match [type status]
    [:annotation :ok]    ann-ok-from-server-schema
    [:annotation :error] ann-error-from-server-schema
    [:notify     _]      {:status s/Keyword
                          :type s/Keyword
                          :data {:message s/Str
                                 :by s/Str}
                          :payload-id s/Any}))

(defn ws-from-client
  [{:keys [type data] :as payload}]
  (case type
    :annotation {:type s/Keyword
                 :data {:hit-id (s/if vector? [s/Int]   s/Int)
                        :ann (s/if vector? [annotation-schema] annotation-schema)}
                 :payload-id s/Any}
    :notify     {:type s/Keyword
                 :data {}
                 :payload-id s/Any}))
