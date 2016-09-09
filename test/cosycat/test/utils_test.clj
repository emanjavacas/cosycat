(ns cosycat.test.utils-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [cosycat.app-utils :refer [is-last-partition]]))

(deftest utility-funtions-test
  (let [length 20, n 3]
    (testing "is-last-partition"
      (is (= '(false false false false false false true)
             (for [[idx _] (map-indexed vector (partition-all n (range length)))]
               (is-last-partition length n idx)))))))
