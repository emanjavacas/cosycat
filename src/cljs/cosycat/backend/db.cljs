(ns cosycat.backend.db)

(defn default-opts-map [key]
  (case key
    :sort-opts {:position "match" :attribute "word" :facet "insensitive"}
    :filter-opts {:attribute "title" :value "random"}))

(defn default-settings
  [& {:keys [corpora] :or {corpora []}}]
  (let [corpus (first (map :corpus corpora))]
    {:notifications {:delay 7500}
     :query {:query-opts {:context 5 :from 0 :page-size 5}
             :sort-opts []
             :filter-opts []
             :snippet-opts {:snippet-size 50 :snippet-delta 25}
             :corpus corpus}}))

(def default-session
  {:active-panel :front-panel
   :active-project nil})

(defn default-project-session [project]
  {:query {:results-summary {}
           :results []
           :results-by-id {}}
   :status {}
   :components {:panel-order ["query-frame" "annotation-panel"]
                :panel-open {"query-frame" true "annotation-panel" false}
                :active-project-frame :users
                :issue-filters {:status "all" :type "all"}
                :event-filters {:type "all"}
                :open-hits #{}
                :token-field :word}
   :filtered-users (into #{} (map :username (:users project)))})

(def default-history {:app-events []})

(def default-project-history {:query []})
