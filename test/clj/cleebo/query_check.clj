(ns cleebo.query-check
  (:require [clojure.test :refer [deftest testing is]]
            [cleebo.query-check :refer [parse-checks check-query check-fn-map]]))


(def test-strs
  [{:s "\"the\"" :should :pass}
   {:s "[pos=\"the\"]" :should :pass}
   {:s "[pos = \"the\"]" :should :pass}
   {:s "[pos=\\."
    :should :fail
    :error {:where 0 :what :missing-bracket}}
   {:s "\"a'"
    :should :fail
    :error {:where 0 :what :missing-quote}}
   {:s "\"a\" [word=\"word\"] " :should :pass}
   {:s "\"a [word=\"word\"] \"a\" \"b\""
    :should :fail
    :error {:where 0 :what :missing-quote}}
   {:s "\"a\" [word=\"word\"] 'a \"b\""
    :should :fail
    :error {:where 2 :what :missing-quote}}
   {:s "\"a\"  [word=\"word]{1,3}  \"a\" \"b\""
    :should :fail
    :error {:where 1 :what :missing-quote-inside}}])

(deftest general-check-test
  (letfn [(parse-check-result [s should]
            (if (some identity (parse-checks (check-query s check-fn-map)))
              :fail
              :pass))]
    (testing "parsing query strings, fail or not?"
      (doseq [{s :s should :should} test-strs]
        (is (= should (parse-check-result s should))
            (str s " should " should))))))

(deftest error-type-check-test
  (letfn [(parse-check-result [s where]
            (let [[s span [what]] (nth (parse-checks (check-query s check-fn-map)) where)]
              what))]
    (testing "parsing query strings, fail or not?"
      (doseq [{s :s {where :where what :what} :error} (filter :error test-strs)]
        (is (= what (parse-check-result s where))
            (str s "should have " what " at " where "th token"))))))

