(ns cosycat.test.events-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [monger.collection :as mc]
            [schema.core :as s]
            [cosycat.test.test-config :refer [db-fixture project-fixture project-data db]]
            [cosycat.schemas.event-schemas :refer [event-schema]]
            [cosycat.db.users :as users]
            [cosycat.db.projects :as proj]))

(use-fixtures :once db-fixture project-fixture)

(def query-event {:data {:query-str "\"a\""} :type :query})

(deftest events-test
  (let [{:keys [creator project-name]} project-data]
    (testing "register new user project event"
      (let [_ (users/register-user-project-event db creator project-name query-event)
            last-event (last (users/user-project-events db creator project-name :max-events 1))]
        (is (not (nil? last-event)))))
    (testing "same user project event"
      (let [_ (users/register-user-project-event db creator project-name {:type :query :data {:query-str ""}})
            _ (users/register-user-project-event db creator project-name query-event)
            _ (users/register-user-project-event db creator project-name query-event)
            {:keys [repeated data]} (last (users/user-project-events db creator project-name :max-events 1))]
        (is (= 2) (count repeated))
        (is (= data (:data query-event)))))))

