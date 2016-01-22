(ns cleebo.query-parser-test
  (:require [clojure.test :refer [deftest testing is]]
            [cleebo.query-parser :refer [missing-quotes]]))

(deftest missing-quotes-status-test
  (let [test-strs [{:s "\"the\" 'thing'"  :status :finished}
                   {:s "\"the' \"thing\"" :status :mismatch :at 0}
                   {:s "the\"  'thing'"   :status :mismatch :at 3}
                   {:s "\"human\" [word='being'] " :status :finished}]]
    (testing "testing messages"
      (doseq [test-str test-strs
              :let [{s :s status :status at :at} (missing-quotes (:s test-str))]]
        (is (= (:status test-str) status))))))

(deftest missing-quotes-at-test
  (let [test-strs [{:s "\"the\" 'thing'"  :status :finished}
                   {:s "\"the' \"thing\"" :status :mismatch :at 0}
                   {:s "the\"  'thing'"   :status :mismatch :at 3}
                   {:s "\"human\" [word='being'] " :status :finished}]]
    (testing "testing messages"
      (doseq [test-str test-strs
              :when (= :mismatch (:status test-str))
              :let [{s :s status :status at :at} (missing-quotes (:s test-str))]]
        (is (= (:at test-str) at))))))
