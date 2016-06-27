(ns cleebo.backend.db)

(defn default-settings
  [& {:keys [corpora] :or {corpora []}}] ;TODO: settings should be adapted to corpus config
  (let [corpus (first corpora)]
    {:notifications {:delay 7500}
     :query {:query-opts {:context 5 :from 0 :page-size 10}
             :sort-opts [{:position "match" :attribute "word" :facet "i"}]
             :filter-opts []
             :snippet-opts {:snippet-size 30 :snippet-delta 15}
             :corpus corpus}}))

(defn default-session
  [& {:keys [corpora] :or {corpora []}}] ;TODO: settings should be adapted to corpus config
  {:active-panel :front-panel
   :active-project nil
   :settings (default-settings :corpora corpora)})

(def default-project-session
  {:query {:results-summary {}
           :results []
           :results-by-id {}}
   :filtered-users #{}})

(def default-history
  {:ws-events []
   :internal-events []})
