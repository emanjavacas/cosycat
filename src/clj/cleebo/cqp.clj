(ns cleebo.cqp
  (:require [com.stuartsierra.component :as component]
            [cqp-clj.core :refer [make-cqi-client connect! disconnect!] :as cqp]
            [cqp-clj.spec :refer [read-init]]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre]))

(defrecord CQiComponent [client init-file]
  component/Lifecycle
  (start [component]
    (let [client (make-cqi-client (read-init init-file))]
      (timbre/info "Connected to CQPServer")
      (assoc component :client client)))
  (stop [component]
    (timbre/info "Shutting down connection to CQPServer")
    (disconnect! (:client component))
    (assoc component :client nil)))

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
      {:results (apply sorted-map (interleave (range from to) results))
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
    (timbre/debug corpus client from to context attrs)
    (let [results (cqp/cpos-seq-handler
                   client
                   corpus
                   (cqp/cpos-range client corpus from to)
                   context
                   attrs)
          to (+ from (count results))]
      {:results (zipmap (range from to) results)
       :from from
       :to to})))

(defn wrap-safe [thunk]
  (try (let [out (thunk)]
         (assoc out :status {:status :ok :status-text "OK"}))
       (catch Exception e
         {:status {:status :error :status-text (str e)}})))

(defn cqi-query [args-map]
  (wrap-safe (fn [] (apply cqi-query* (apply concat args-map)))))

(defn cqi-query-range [args-map]
  (wrap-safe (fn [] (apply cqi-query-range* (apply concat args-map)))))

;; (def spec (read-init "dev-resources/cqpserver.init"))
;; (def client (cqp/make-cqi-client spec))
;; (def query-str "'goin.*' @'.*' 'to'")
;; (def query-str "'the'")
;; (def attrs
;;   (create-attrs [{:type :pos :name "word"} {:type :pos :name "pos"}]))

;; (def result
;;   (do (cqp/query! client "PYCCLE-ECCO" "'those'" "latin1")
;;       (cqp/cpos-seq-handler
;;        client
;;        "PYCCLE-ECCO"
;;        (cqp/cpos-range client "PYCCLE-ECCO" 0 10)
;;        2
;;        attrs)))

;; (def query-size
;;   (cqp/query-size client "PYCCLE-ECCO"))

;; (prn query-size)

;; (def hits
;;   (cqp/cpos-seq-handler
;;    client
;;    "PYCCLE-ECCO"
;;    (cqp/cpos-range client "PYCCLE-ECCO" 0 query-size)
;;    2
;;    attrs))
