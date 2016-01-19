(ns cleebo.blacklab
  (:require [taoensso.timbre :as timbre]
            [cleebo.blacklab.core :refer
             [query query-range sorted-range sorted-query query-size new-blsearcher]]))

(defn format-hit [hit context]
  (let [match-idxs (keep-indexed (fn [i hit] (when (:match hit) i)) hit)
        left  (- context (first match-idxs))
        right (- context (- (count hit) (inc (last match-idxs))))]
    (concat (map (fn [id] {:id id :word ""}) (range left))
            hit
            (map (fn [id] {:id id :word ""}) (range right)))))

(defn numerize-hits
  "{0 {:hit [{:id id :word word} {:id id :word word}] :num 0 :meta meta}}"
  [hits from to context]
  (let [formatted (map (fn [hit-map] (update hit-map :hit #(format-hit % context))) hits)]
    (apply array-map (interleave (range from to) formatted))))

(defn- wrap-safe [f]
  (fn [& args]
    (try (let [out (apply f args)]
           (assoc out :status {:stutus :ok :status-text "OK"}))
         (catch Exception e
           {:status {:status :error :status-text (str e)}}))))

(defn- bl-query*
  ([searcher corpus query-str from to context]
   (bl-query* searcher corpus query-str from to context "default"))
  ([searcher corpus query-str from to context query-id]
   (let [hits (query searcher corpus query-str from to context query-id)]
     {:results (numerize-hits hits from to context)
      :from from
      :to to
      :query-str query-str
      :query-size (query-size searcher corpus query-id)})))

(defn- bl-query-range*
  ([searcher corpus from to context sort-map]
   (bl-query-range* searcher corpus from to context sort-map "default"))
  ([searcher corpus from to context {:keys [criterion prop-name]} query-id]
   (let [hits (if (and criterion prop-name)
                (sorted-range searcher corpus from to context criterion prop-name query-id)
                (query-range searcher corpus from to context query-id))]
     {:results (numerize-hits hits from to context)
      :from from
      :to to})))

(defn- bl-sort-query*
  ([searcher corpus from to context sort-map]
   (bl-sort-query* searcher corpus from to context sort-map "default"))
  ([searcher corpus from to context {:keys [criterion prop-name]} query-id]
   (let [hits (sorted-query searcher corpus from to context criterion prop-name query-id)]
     {:results (numerize-hits hits from to context)
      :from from
      :to to})))

(def bl-query (wrap-safe bl-query*))
(def bl-query-range (wrap-safe bl-query-range*))
(def bl-sort-query (wrap-safe bl-sort-query*))

