(ns cosycat.routes.utils
  (:require [ring.util.response :refer [redirect]]
            [buddy.auth :refer [authenticated?]]
            [taoensso.timbre :as timbre]
            [cosycat.app-utils :refer [span->token-id deep-merge-with dekeyword]]
            [cosycat.roles :refer [check-annotation-role]]
            [cosycat.db.annotations :refer [find-annotation-owner]]
            [cosycat.db.projects :refer [find-project-by-name]]))

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
  [router & {:keys [is-ok? login-uri] :as rule-map}]
  (safe (fn [req] {:status 200 :body (router req)}) rule-map))

(defn format-stacktrace [stacktrace]
  (apply str (interleave (repeat "\t") stacktrace (repeat "\n"))))

(defn make-default-route
  "a router that transform internal errors into proper responses"
  [route & {:keys [is-ok? login-uri] :as rule-map}]
  (safe (fn [req]
          (try {:status 200 :body (route req)}
               (catch clojure.lang.ExceptionInfo e
                 (let [{:keys [message code data]} (ex-data e)]
                   (timbre/debug "Caught ExceptionInfo:" (ex-data e))
                   {:status 500 :body {:message message :code code :data data}}))
               (catch Exception e
                 (let [{message :message ex :class} (bean e)
                       stacktrace (mapv str (.getStackTrace e))]
                   (timbre/debug "Caught java.lang.Exception: [" (str ex) "]\n"
                                 "Stacktrace:\n" (format-stacktrace stacktrace))
                   {:status 500
                    :body {:message message :data {:e (str ex) :stacktrace stacktrace}}}))))
        rule-map))

(defn unwrap-arraymap
  "somehow cljs vectors are parsed as arraymaps at the server (transit bug?)"
  [a]
  (if (vector? a) a (vals a)))

;;; Exceptions
(defn ex-user [username project-name action]
  (let [message (format "%s is not authorized to %s in project [%s]"
                        username (dekeyword action) project-name)]
    (ex-info message
     {:code :not-authorized
      :message message
      :data {:username username :action action :project project-name}})))

(defn find-user-role
  "returns user role in project or `owner` if username is current annotation owner.
   `ann-id` might be nil, in which case role defaults to user project role"
  [db username project-name project-users ann-id]
  (let [{role :role} (some #(when (= username (:username %)) %) project-users)]
    (if (and (not (nil? ann-id)) (= username (find-annotation-owner db project-name ann-id)))
      "owner"
      role)))

(defn check-user-rights
  "check user rights to an action inside a project. `ann-id` can optionally be provided
   in which case `owner` might be returned as role."
  [db username project-name action & [ann-id]]
  (let [{users :users} (find-project-by-name db project-name)
        role (find-user-role db username project-name users ann-id)]
    (when-not (check-annotation-role action role)
      (throw (ex-user username project-name action)))))

;;; Normalizers
(defn ann->maps
  [{{type :type :as span} :span {key :key} :ann :as ann}]
  (let [token-id-or-ids (span->token-id span)]
    (case type
      "token" {token-id-or-ids {key ann}}
      "IOB" (zipmap token-id-or-ids (repeat {key ann})))))

(defn normalize-anns
  "converts incoming annotations into a map of token-ids to ann-keys to anns"
  [& anns]
  (->> anns (map ann->maps) (apply deep-merge-with merge)))
