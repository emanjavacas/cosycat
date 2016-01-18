(ns cleebo.blacklab
  (:require [taoensso.timbre :as timbre]
            [cleebo.blacklab.core
             :refer [query query-range sorted-range query-size new-blsearcher]]))

(defn bl-query*
  ([searcher corpus query-str from to context]
   (bl-query* searcher corpus query-str from to context "default"))
  ([searcher corpus query-str from to context query-id]
   (let [hits (query searcher corpus query-str from to context query-id)]
     {:results  (map (fn [hit num] (assoc hit :num num)) hits (range from to))
      :from from
      :to to
      :query-str query-str
      :query-size (query-size searcher corpus query-id)})))

(defn bl-query-range*
  ([searcher corpus from to context sort-map]
   (bl-query-range* searcher corpus from to context sort-map "default"))
  ([searcher corpus from to context {:keys [criterion prop-name]} query-id]
   (let [hits (if (and criterion prop-name)
                (sorted-range searcher corpus from to context criterion prop-name query-id)
                (query-range searcher corpus from to context query-id))]
     {:results  (map (fn [hit num] (assoc hit :num num)) hits (range from to))
      :from from
      :to to})))

(defn bl-sort-query* [])                ;todo

(defn- wrap-safe [thunk]
  (try (let [out (thunk)]
         (assoc out :status {:stutus :ok :status-text "OK"}))
       (catch Exception e
         {:status {:status :error :status-text (str e)}})))

(defn bl-query [& args]
  (apply bl-query* args)
  (wrap-safe (fn [] (apply bl-query* args))))

(defn bl-query-range [& args]
  (apply bl-query-range* args)
  (wrap-safe (fn [] (apply bl-query-range* args))))
