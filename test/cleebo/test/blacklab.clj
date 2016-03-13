(ns cleebo.test.blacklab
  (:require [cleebo.components.blacklab :as bl]
            [clojure.test :refer [deftest testing is]]))

(def path-maps {"brown-id" "/home/enrique/code/BlackLab/brown-index-id"})

(deftest bl-query-test
  (testing "Runtime Exception on bl-query-test"
    (is (= :ok (bl/with-bl-component [bl-component
                                      (-> (bl/new-bl path-maps) (.start))]
                 (let [result (bl/bl-query bl-component "brown-id" "\"a\"" 0 10 5)]
                   (get-in result [:status :status]))))
        "status should be :ok")))

(deftest bl-query-range-test
  (testing "Runtime Exception on bl-query-range-test"
    (is (= :ok (bl/with-bl-component [bl-component
                                      (-> (bl/new-bl path-maps) (.start))]
                 (bl/bl-query bl-component "brown-id" "\"a\"" 0 10 5)
                 (let [result (bl/bl-query-range bl-component "brown-id" 0 10 5)]
                   (get-in result [:status :status]))))
        "status should be :ok")))

(deftest bl-sort-query-test
  (testing "Runtime Exception on bl-sort-query-test"
    (is (= :ok (bl/with-bl-component [bl-component
                                      (-> (bl/new-bl path-maps) (.start))]
                 (bl/bl-query bl-component "brown-id" "\"a\"" 0 10 5)
                 (let [result (bl/bl-sort-query bl-component "brown-id" 0 10 5
                                                {:criterion :left-context
                                                 :prop-name "word"})]
                   (get-in result [:status :status]))))
        "status should be :ok")))

(deftest bl-sort-range-test
  (testing "Runtime Exception on bl-sort-range-test"
    (is (= :ok (bl/with-bl-component [bl-component
                                      (-> (bl/new-bl path-maps) (.start))]
                 (bl/bl-query bl-component "brown-id" "\"a\"" 0 10 5)
                 (let [result (bl/bl-sort-range bl-component "brown-id" 0 10 5
                                                {:criterion :left-context
                                                 :prop-name "word"})]
                   (get-in result [:status :status]))))
        "status should be  :ok")))

