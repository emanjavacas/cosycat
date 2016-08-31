(ns cleebo.backend.db)

(defn default-opts-map [key]
  (case key
    :sort-opts {:position "match" :attribute "word" :facet "insensitive"}
    :filter-opts {:attribute "title" :value "random"}))

(defn default-settings
  [& {:keys [corpora] :or {corpora []}}] ;TODO: settings should be adapted to corpus config
  (let [corpus (first (map :name corpora))]
    {:notifications {:delay 7500}
     :query {:query-opts {:context 5 :from 0 :page-size 5}
             :sort-opts []
             :filter-opts []
             :snippet-opts {:snippet-size 50 :snippet-delta 25}
             :corpus corpus}}))

(def default-session {:active-panel :front-panel :active-project nil})

(defn default-project-session [project]
  {:query {:results-summary {}
           :results []
           :results-by-id {}}
   :status {}
   :components {:panel-order ["query-frame" "annotation-panel"]
                :panel-open {"query-frame" true "annotation-panel" false}
                :open-hits #{}}
   :filtered-users (into #{} (map :username (:users project)))})

(def default-history {:server-events [] :internal-events []})

(def default-project-history {:query []})
