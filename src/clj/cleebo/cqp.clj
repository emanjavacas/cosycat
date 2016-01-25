(ns cleebo.cqp
  (:require [com.stuartsierra.component :as component]
            [cqp-clj.core :refer [make-cqi-client connect! disconnect!] :as cqp]
            [cqp-clj.spec :refer [read-init]]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre]))

(defrecord CQiComponent [client init-file]
  component/Lifecycle
  (start [component]
    (try
      (let [client (make-cqi-client (read-init init-file))]
        (timbre/info "Connected to CQPServer")
        (assoc component :client client))
      (catch Exception e
        (timbre/info "CQP service not available due to Exception:" (str e))
        (assoc component :client nil))))
  (stop [component]
    (timbre/info "Shutting down connection to CQPServer")
    (if-let [client (:client component)]
      (do
        (disconnect! (:client component))
        (assoc component :client nil))
      component)))

(defn new-cqi-client [{:keys [init-file]}]
  (map->CQiComponent {:init-file init-file}))

(defn create-attrs [pairs]
  (map
   (fn [{type :type name :name}] {:attr-type type :attr-name name})
   pairs))

(def default-attrs
  (create-attrs [{:type :pos :name "word"} {:type :pos :name "pos"}]))

(def encodings
  {"PYCCLE-ECCO" "latin1"})

(defn cqi-query* [& {:keys [cqi-client corpus query-str opts]}]
  {:pre [(and cqi-client corpus query-str)]}
  (let [client (:client cqi-client)
        encoding (get encodings corpus "utf8")
        {:keys [context attrs size from to]
         :or {context 5
              size 10
              from 0
              to (+ from size)
              attrs default-attrs}} opts]
    (cqp/query! client corpus query-str encoding)
    (let [results (cqp/cpos-seq-handler
                      client
                      corpus
                      (cqp/cpos-range client corpus from to)
                      context
                      attrs)
          to (+ from (count results))]
      {:results (mapv (fn [hit num] {:hit hit :num num}) results (range from to))
       :from from
       :to to
       :query-str query-str
       :query-size (cqp/query-size client corpus)})))

(defn cqi-query-range* [& {:keys [cqi-client corpus from to opts]}]
  {:pre [(and cqi-client corpus from to)]}
  (let [client (:client cqi-client)
        {:keys [context attrs]
         :or {context 5
              attrs default-attrs}} opts]
    (let [results (cqp/cpos-seq-handler
                   client
                   corpus
                   (cqp/cpos-range client corpus from to)
                   context
                   attrs)
          to (+ from (count results))]
      {:results (mapv (fn [hit num] {:hit hit :num num}) results (range from to))
       :from from
       :to to})))

(defn- wrap-safe [thunk]
  (try (let [out (thunk)]
         (assoc out :status {:status :ok :status-content "OK"}))
       (catch Exception e
         {:status {:status :error :status-content (str e)}})))

(defn cqi-query [args-map]
  (wrap-safe (fn [] (apply cqi-query* (apply concat args-map)))))

(defn cqi-query-range [args-map]
  (wrap-safe (fn [] (apply cqi-query-range* (apply concat args-map)))))

