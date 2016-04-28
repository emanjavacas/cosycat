(ns cleebo.main
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [cleebo.components.http-server :refer [new-http-server]]
            [cleebo.components.db :refer [new-db]]
            [cleebo.components.blacklab :refer [new-bl]]
            [cleebo.components.ws :refer [new-ws]]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [clojure.string :as str])
  (:gen-class))

(set! *warn-on-reflection* true)

;;; production system
(def prod-config-map
  {:port (env :port)
   :database-url (env :database-url)
   :cqp-init-file (env :cqp-init-file)
   :blacklab-paths-map (env :blacklab-paths-map)})

(defn create-prod-system [config-map]
  (let [{:keys [handler port database-url blacklab-paths-map]} config-map]
    (-> (component/system-map
         :blacklab (new-bl blacklab-paths-map)
         :db (new-db {:url database-url})
         :ws (new-ws)
         :http-server (new-http-server {:port port :components [:db :ws :blacklab]}))
        (component/system-using
         {:http-server [:db :ws :blacklab]
          :blacklab    [:ws]
          :ws          [:db]}))))

(defn usage [options-summary]
  (->> ["Welcome to the command line interface of Cleebo (Corpus Linguistics with EEBO)"
        ""
        "Usage: path/to/jar [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  start      Starts the web-server"
        "  clean      Cleans the app environment in the cwd"]
       (string/join \newline)))

(def cli-options [])

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn this-jar-path [& {:keys [ns] :or {ns cleebo.main}}]
  (-> (or ns (class *ns*))
      .getProtectionDomain
      .getCodeSource
      .getLocation
      io/file
      .getParentFile
      .getPath))

(defn cwd []
  (System/getProperty "user.dir"))

(defn ensure-dynamic-resource-path []
  (let [resource-path (:dynamic-resource-path env)
        avatar-path (:avatar-path env)
        cwd-path (cwd)
        jar-path (this-jar-path)]
    (timbre/info "Starting app in [" cwd-path "]")
    (timbre/info "Jar executable located in path [" jar-path "]")
    (when-not (= cwd-path jar-path)
      (do (timbre/warn "The app isn't running in the same dir as it is located.\n"
                       "You might result into troubles.\n"
                       "Stopping the application now...")
          (System/exit 1)))
    (when-not (.exists (io/file resource-path))
      (io/make-parents (str resource-path avatar-path "dummy")))))

(defn run-server []
  (let [^com.stuartsierra.component.SystemMap system (create-prod-system prod-config-map)]
    (ensure-dynamic-resource-path)
    (.addShutdownHook 
     (Runtime/getRuntime) 
     (Thread. (fn [] (.stop system) (shutdown-agents))))
    (.start system)))

(defn safe-delete [file-path]
  (if (.exists (io/file file-path))
    (try
      (io/delete-file file-path)
      (catch Exception e (str "exception: " (.getMessage e))))
    false))

(defn delete-directory [directory-path]
  (let [directory-contents (file-seq (io/file directory-path))
        files-to-delete (filter #(.isFile %) directory-contents)]
    (doseq [file files-to-delete]
      (safe-delete (.getPath file)))
    (safe-delete directory-path)))

(defn clean-env []
  (let [root (io/file (:dynamic-resource-path env))]
    (delete-directory root)))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    (case (first arguments)
      "start" (run-server)
      "clean" (clean-env)
      (exit 1 (usage summary)))))
