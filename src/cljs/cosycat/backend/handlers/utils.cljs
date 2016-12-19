(ns cosycat.backend.handlers.utils)

(defn get-query
  "get query from query results summary for :query dispatches"
  [db project-name]
  (get-in db [:projects project-name :session :query :results :results-summary :query-str]))

(defn get-corpus-param
  "get corpus from query results summary for :query dispatches and ensure a corpus is return"
  [db project-name db-path corpus-param]
  {:pre [(or (= db-path :query) (not (nil? corpus-param)))]
   :post [(not (nil? %))]}
  (case db-path
    :query (get-in db [:projects project-name :session :query :results :results-summary :corpus])
    :review corpus-param))

(defn expand-db-path [db-path]
  (case db-path
    :query  [:session :query :results :results-by-id]
    :review [:session :review :results :results-by-id]))
