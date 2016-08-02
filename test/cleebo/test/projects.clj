(ns cleebo.test.projects
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [cleebo.test.test-config :refer [db-fixture db]]))

(use-fixtures :once db-fixture)




