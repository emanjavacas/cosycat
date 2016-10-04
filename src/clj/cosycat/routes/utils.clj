(ns cosycat.routes.utils
  (:require [ring.util.response :refer [redirect]]
            [buddy.auth :refer [authenticated?]]
            [taoensso.timbre :as timbre]))

(defn safe
  [handler & [rule-map]]
  (fn [req]
    (let [{:keys [login-uri is-ok?] :or {is-ok? authenticated? login-uri "/login"}} rule-map]
      (if (is-ok? req)
        (handler req)
        (-> (redirect login-uri)
            (assoc-in [:session :next] (:uri req)))))))

(defn make-safe-route
  "a router that always returns success responses (error are handled internally in client)"
  [router & {:keys [is-ok? login-uri] :or {is-ok? authenticated? login-uri "/login"}}]
  (safe (fn [req] {:status 200 :body (router req)}) {:login-uri login-uri :is-ok? is-ok?}))

(defn make-default-route
  "a router that transform internal errors into proper responses"
  [route & {:keys [is-ok? login-uri] :as rule-map}]
  (safe (fn [req]
          (try {:status 200 :body (route req)}
               (catch clojure.lang.ExceptionInfo e
                 (let [{:keys [message code data]} (ex-data e)]
                   {:status 500 :body {:message message :code code :data data}}))
               (catch Exception e
                 (let [{message :message ex :class} (bean e)
                       stacktrace (mapv str (.getStackTrace e))]
                   {:status 500
                    :body {:message message :data {:e (str ex) :stacktrace stacktrace}}}))))
        rule-map))

(defn unwrap-arraymap
  "somehow cljs vectors are parsed as arraymaps at the server (transit bug?)"
  [a]
  (if (vector? a) a (vals a)))
