(ns cleebo.test.blacklab
  (:require [cleebo.components.blacklab :as bl]
            [environ.core :refer [env]]
            [clojure.test :refer [deftest testing is]]))

(def path-maps (:blacklab-paths-map env))
(def corpus (first (:corpora env)))

(deftest bl-query-test
  (testing "Runtime Exception on bl-query-test"
    (is (= :ok (bl/with-bl [bl-component (-> (bl/new-bl path-maps) (.start))]
                 (-> (bl/bl-query bl-component corpus "\"a\"" 0 10 5)
                     (get-in [:status :status]))))
        "status should be :ok")))

(deftest bl-query-range-test
  (testing "Runtime Exception on bl-query-range-test"
    (is (= :ok (bl/with-bl [bl-component (-> (bl/new-bl path-maps) (.start))]
                 (bl/bl-query bl-component corpus "\"a\"" 0 10 5)
                 (-> (bl/bl-query-range bl-component corpus 0 10 5)
                     (get-in [:status :status]))))
        "status should be :ok")))

(deftest bl-sort-query-test
  (testing "Runtime Exception on bl-sort-query-test"
    (is (= :ok (bl/with-bl [bl-component (-> (bl/new-bl path-maps) (.start))]
                 (bl/bl-query bl-component corpus "\"a\"" 0 10 5)
                 (let [sort-map {:criterion :left-context :prop-name "word"}]
                   (-> (bl/bl-sort-query bl-component corpus 0 10 5 sort-map)
                       (get-in [:status :status])))))
        "status should be :ok")))

(deftest bl-sort-range-test
  (testing "Runtime Exception on bl-sort-range-test"
    (is (= :ok (bl/with-bl [bl-component (-> (bl/new-bl path-maps) (.start))]
                 (bl/bl-query bl-component corpus "\"a\"" 0 10 5)
                 (let [sort-map {:criterion :left-context :prop-name "word"}]
                   (-> (bl/bl-sort-range  bl-component corpus 0 10 5 sort-map)
                       (get-in[:status :status])))))
        "status should be :ok")))
