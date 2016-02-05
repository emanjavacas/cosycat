(ns cleebo.test.routes
  (:require [cleebo.routes.cqp :as cqp-route]
            [cleebo.cqp :as cqp]
            [cleebo.routes.blacklab :as bl-route]
            [cleebo.blacklab :as bl]
            [environ.core :refer [env]]
            [clojure.test :refer [deftest testing is]]))

(defn flatten-keys [m]
  (if (not (map? m))
    {[] m}
    (into {}
          (for [[k v] m
                [ks v'] (flatten-keys v)]
            [(cons k ks) v']))))

(deftest flatten-keys-test
  (testing "how helper function works"
    (is (= (keys (flatten-keys {:a {:a1 "a1" :b1 {:a2 "a2"}}}))
           '((:a :a1) (:a :b1 :a2))))))

(deftest bl-route
  (let [path-maps (get-in env [:blacklab :blacklab-paths-map] env)
        bl-component (-> (bl/new-bl-component path-maps) (.start))
        bl-out (bl-route/bl-query-route
                {:session {:identity {:username "foo"}}
                 :components {:blacklab bl-component}
                 :params {:corpus "brown-id"
                          :query-str "\"a\""
                          :context "5"
                          :size "3"
                          :from "0"}})
        cqp-init (get-in env [:cqp :cqp-init-file])
        cqi-client (-> (cqp/new-cqi-client {:init-file cqp-init}) (.start))
        cqp-out (cqp-route/cqp-query-route
                 {:components {:cqi-client cqi-client}
                  :params {:corpus "DICKENS"
                           :query-str "\"a\""
                           :context "5"
                           :size "3"
                           :from "0"}})
        filter-meta (fn [coll] (remove (partial some #{:meta}) coll))]
    (.stop bl-component)
    (.stop cqi-client)
    (testing "I/O for blacklab routes"
      (is (= (sort [[:results 0 :hit] [:results 1 :hit] [:results 2 :hit]
                    [:from]
                    [:to]
                    [:query-str]
                    [:query-size]
                    [:status :status]
                    [:status :status-content]])
             (sort (mapv vec (filter-meta (keys (flatten-keys cqp-out)))))
             (sort (mapv vec (filter-meta (keys (flatten-keys bl-out))))))))))

