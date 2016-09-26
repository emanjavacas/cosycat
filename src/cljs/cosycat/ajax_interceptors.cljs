(ns cosycat.ajax-interceptors
  (:require [ajax.core :refer [default-interceptors to-interceptor]]))

;;; util
(defn debug-response [res]
  (.log js/console
        "Response:" (try (.getResponseJson res)
                         (catch :default e
                           "couldn't parse response")))
  res)

(defn debug-request [req]
  (.log js/console "Request:" req)
  req)

;;; interceptors
(defn csrf-interceptor [{:keys [csrf-token]}]
  (to-interceptor
   {:name "CSRF-Token interceptor"
    :request #(assoc-in % [:params :csrf] csrf-token)}))

(defn ajax-header-interceptor []
  (to-interceptor
   {:name "AJAX-Header interceptor"
    :request #(assoc-in % [:headers "X-Requested-With"] "XMLHttpRequest")}))

(defn debug-interceptor []
  (to-interceptor
   {:name "Debug interceptor"
    :response debug-response
    :request debug-request}))

(defn add-interceptor [interceptor & args]
  (swap! default-interceptors (partial cons (apply interceptor args))))
