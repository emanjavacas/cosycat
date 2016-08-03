(ns cleebo.test.annotations-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.test.check.generators :as check-generators]
            [monger.collection :as mc]
            [schema.core :as s]
            [cleebo.test.test-config :refer [db-fixture db]]
            [cleebo.schemas.annotation-schemas :refer [annotation-schema]]
            [cleebo.db.annotations :as anns]))

(def project "_test_project")
(defn test-project-fixture [f]
  (mc/drop (:db db) project)
  (f)
  (mc/drop (:db db) project))

(use-fixtures :once db-fixture test-project-fixture)

;;; Annotations
(def payload
  {:hit-id  [5377 9569],
   :ann-map [{:ann      {:key "a", :value "a"},
              :username "user",
              :span     {:type "token", :scope 166}
              :corpus "my-corpus"
              :query "my-query"
              :timestamp 1461920859355}
             {:ann {:key "a", :value "a"},
              :username "user",
              :span {:type "token", :scope 297}
              :corpus "my-corpus"
              :query "my-query"
              :timestamp 1461920859357}]})

(def token-ann
  {:ann {:key "a", :value "a"},
   :username "user",
   :span     {:type "token", :scope 166},
   :corpus "my-corpus"
   :query "my-query"
   :timestamp 1461920859355})

(def IOB-ann
  {:ann {:key "a", :value "a"},
   :username "user",
   :span     {:type "IOB", :scope {:B 160 :O 165}}
   :corpus "my-corpus"
   :query "my-query"   
   :timestamp 1461920859355})

(def overlapping-IOB-IOB-ann
  {:ann {:key "a", :value "a"},
   :username "user",
   :span     {:type "IOB", :scope {:B 159 :O 162}}
   :corpus "my-corpus"
   :query "my-query" 
   :timestamp 1461920859355})

(def overlapping-token-IOB-ann
  {:ann {:key "a", :value "a"},
   :username "user",
   :span     {:type "IOB", :scope {:B 164 :O 168}}
   :corpus "my-corpus"
   :query "my-query"     
   :timestamp 1461920859355})

(def overlapping-IOB-token-ann
  {:ann {:key "a", :value "a"},
   :username "user",
   :span     {:type "token", :scope 164},
   :corpus "my-corpus"
   :query "my-query"  
   :timestamp 1461920859355})

(deftest insert-annotation-test
  (testing "insert token annotation"
    (let [ann-out (anns/insert-annotation db project token-ann)]
      (is (nil? (s/check annotation-schema ann-out)))))
  (testing "find token annotation"
    (is (let [k (get-in token-ann [:ann :key]) 
              span (:span token-ann)
              ann (anns/fetch-token-annotation-by-key db project k span)]
          (nil? (s/check annotation-schema ann)))))
  (testing "insert IOB annotation"
    (let [k (get-in IOB-ann [:ann :key])
          span (:span IOB-ann)]
      (is (nil? (s/check annotation-schema (anns/insert-annotation db project IOB-ann))))))
  (testing "find IOB annotation"
    (is (let [k (get-in IOB-ann [:ann :key]) 
              span (:span IOB-ann)
              ann (anns/fetch-span-annotation-by-key db project k span)]
          (nil? (s/check annotation-schema ann)))))
  (testing "attempt IOB overlapping IOB annotation"
    (is (= (-> (try (anns/insert-annotation db project overlapping-IOB-IOB-ann)
                    (catch clojure.lang.ExceptionInfo e
                      (ex-data e)))
               (select-keys [:source-scope :scope]))
           {:source-scope (get-in IOB-ann [:span :scope])
            :scope (get-in overlapping-IOB-IOB-ann [:span :scope])})))
  (testing "attempt token overlapping IOB annotation"
    (is (= (-> (try (anns/insert-annotation db project overlapping-token-IOB-ann)
                    (catch clojure.lang.ExceptionInfo e
                      (ex-data e)))
               (select-keys [:scope :source-scope]))
           {:source-scope (get-in token-ann [:span :scope])
            :scope (get-in overlapping-token-IOB-ann [:span :scope])})))
  (testing "attempt IOB overlapping token annotation"
    (is (= (-> (try (anns/insert-annotation db project overlapping-IOB-token-ann)
                    (catch clojure.lang.ExceptionInfo e
                      (ex-data e)))
               (select-keys [:source-scope :scope]))
           {:source-scope (get-in IOB-ann [:span :scope])
            :scope (get-in overlapping-IOB-token-ann [:span :scope])}))))
