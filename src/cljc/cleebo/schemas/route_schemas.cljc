(ns cleebo.schemas.route-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [cleebo.schemas.annotation-schemas :refer [annotation-schema]]
            [taoensso.timbre :as timbre]
            #?(:clj [clojure.core.match :refer [match]]
               :cljs [cljs.core.match :refer-macros [match]])))

(def ann-ok-from-server-schema
  "multiple anns implies multiple hit-ids"
  {:status s/Keyword
   :type   s/Keyword
   :data   {:hit-id   (s/if vector? [s/Int] s/Int)
            :ann-map  (s/if vector? [annotation-schema] annotation-schema)}
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
                          (s/optional-key :payload-id) s/Any}))

(defn ws-from-client
  [{:keys [type data] :as payload}]
  (case type
    :annotation {:type s/Keyword
                 :data {:hit-id  (s/if vector? [s/Int]   s/Int)
                        :ann-map (s/if vector? [annotation-schema] annotation-schema)}
                 :payload-id s/Any}
    :notify     {:type s/Keyword
                 :data {}
                 (s/optional-key :payload-id) s/Any}))
