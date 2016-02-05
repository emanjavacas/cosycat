(ns cleebo.cqp
  (:require [com.stuartsierra.component :as component]
            [cleebo.utils :refer [wrap-safe]]
            [cqp-clj.core :refer [make-cqi-client connect! disconnect!] :as cqp]
            [cqp-clj.spec :refer [read-init]]
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

(defn numerize-hits [hits from to context]
  {:pre (= (count hits) (- to from))}
  (let [formatted (mapv (fn [hit] {:hit hit}) hits)]
    (apply array-map (interleave (range from to) formatted))))

(defn cqi-query* [cqi-client corpus query-str from to context & {:keys [attrs]}]
  (let [client (:client cqi-client)
        encoding (get encodings corpus "utf8")
        attrs (or attrs default-attrs)]
    (cqp/query! client corpus query-str encoding)
    (let [hits (cqp/cpos-seq-handler client corpus (cqp/cpos-range client corpus from to)
                                        context attrs)
          to (+ from (count hits))]
      {:results (numerize-hits hits from to context)
       :from from
       :to to
       :query-str query-str
       :query-size (cqp/query-size client corpus)})))

(defn cqi-query-range* [cqi-client corpus from to context & {:keys [attrs]}]
  (let [client (:client cqi-client)
        attrs (or attrs default-attrs)]
    (let [hits (cqp/cpos-seq-handler
                client corpus (cqp/cpos-range client corpus from to) context attrs)
          to (+ from (count hits))]
      {:results (numerize-hits hits from to context)
       :from from
       :to to})))

(def cqi-query (wrap-safe cqi-query*))

(def cqi-query-range (wrap-safe cqi-query-range*))

