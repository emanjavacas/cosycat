(ns cosycat.test.blacklab-test
  (:require [cosycat.components.blacklab :as bl]
            [cosycat.test.test-config :refer [db-fixture]]
            [config.core :refer [env]]
            [clojure.test :refer [deftest testing is use-fixtures]]))

(def paths-maps (bl/paths-map-from-corpora (:corpora env)))
(def corpus (->> (:corpora env) (filter #(= :blacklab (:type %))) (map :name) first))

(defn bl-fixture [f]
  (def bl-component (-> (bl/new-bl paths-maps) (.start)))
  (f)
  (.stop bl-component))

(use-fixtures :once db-fixture bl-fixture)

(deftest bl-query-test
  (testing "Runtime Exception on bl-query-test"
    (is (= :ok (-> (bl/bl-query bl-component corpus "\"a\"" 0 10 5)
                   (get-in [:status :status])))
        "status should be :ok")))

(deftest bl-query-range-test
  (testing "Runtime Exception on bl-query-range-test"
    (is (= :ok (do
                 (bl/bl-query bl-component corpus "\"a\"" 0 10 5)
                 (-> (bl/bl-query-range bl-component corpus 0 10 5)
                     (get-in [:status :status]))))
        "status should be :ok")))

(deftest bl-sort-query-test
  (testing "Runtime Exception on bl-sort-query-test"
    (is (= :ok (do
                 (bl/bl-query bl-component corpus "\"a\"" 0 10 5)
                 (let [sort-map {:criterion :left-context :prop-name "word"}]
                   (-> (bl/bl-sort-query bl-component corpus 0 10 5 sort-map)
                       (get-in [:status :status])))))
        "status should be :ok")))

(deftest bl-sort-range-test
  (testing "Runtime Exception on bl-sort-range-test"
    (is (= :ok (do
                 (bl/bl-query bl-component corpus "\"a\"" 0 10 5)
                 (let [sort-map {:criterion :left-context :prop-name "word"}]
                   (-> (bl/bl-sort-range  bl-component corpus 0 10 5 sort-map)
                       (get-in[:status :status])))))
        "status should be :ok")))
