(ns cosycat.middleware)

(defn wrap-safe
  "turns eventual exception into a proper response body"
  [f]
  (fn [& args]
    (try (let [out (apply f args)]
           (assoc out :status {:status :ok :status-content "OK"}))
         (catch Exception e
           {:status {:status :error :status-content (str e)}}))))
