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

(defn wrap-safe [f]
  (try (let [out (f)]
         (assoc out :status :ok))
       (catch Exception e
         {:msg (str e)
          :status :error})))

(defn cqi-query [cqi-client corpus query-str & [opts]]
  (let [client (:client cqi-client)
        encoding (get encodings corpus "utf8")
        {:keys [corpus context attrs size from to]
         :or {corpus "PYCCLE-ECCO"
              context 5
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
                   attrs)]
      {:results results
       :from from
       :to (+ from (count results))
       :query-str query-str
       :query-size (cqp/query-size client corpus)})))

(defn query-range [cqi-client corpus from to & [opts]]
  (let [client (:client cqi-client)
        {:keys [corpus context attrs]
         :or {corpus "PYCCLE-ECCO"
              context 5
              attrs default-attrs}} opts]
    (timbre/debug corpus client from to context attrs)
    (let [results (cqp/cpos-seq-handler
                   client
                   corpus
                   (cqp/cpos-range client corpus from to)
                   context
                   attrs)]
      {:results results
       :from from
       :to (+ from (count results))})))

;; (def spec (read-init "dev-resources/cqpserver.init"))
;; (def client (cqp/make-cqi-client spec))
;; (def query-str "'goin.*' @'.*' 'to'")
;; (def query-str "'the'")
;; (def attrs
;;   (create-attrs [{:type :pos :name "word"} {:type :pos :name "pos"}]))

;; (def result
;;   (do (cqp/query! client "PYCCLE-ECCO" query-str "latin1")
;;       (cqp/cpos-seq-handler
;;        client
;;        "PYCCLE-ECCO"
;;        (cqp/cpos-range client "PYCCLE-ECCO" 0 10)
;;        2
;;        attrs)))

;; (cqp/cpos-seq-handler
;;  client
;;  "PYCCLE-ECCO"
;;  (cqp/cpos-range client "PYCCLE-ECCO" 20 40)
;;  2
;;  attrs)
