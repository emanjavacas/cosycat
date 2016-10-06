(ns cosycat.main
     (:require [com.stuartsierra.component :as component]
               [clojure.tools.namespace.repl :refer [refresh refresh-all]]
               [cosycat.utils :refer [prn-format delete-directory]]
               [cosycat.db-utils :refer [clear-dbs]]
               [cosycat.components.http-server :refer [new-http-server]]
               [cosycat.components.db :refer [new-db colls]]
               [cosycat.components.ws :refer [new-ws]]
               [clojure.tools.cli :refer [parse-opts]]
               [clojure.java.io :as io]
               [clojure.string :as string]
               [taoensso.timbre :as timbre]
               [config.core :refer [env]]
               [clojure.string :as str])
     (:gen-class))

(set! *warn-on-reflection* true)

;;; production system
(def prod-config-map
  {:port (env :port)
   :database-url (env :database-url)
   :corpora (env :corpora)})

(defn create-prod-system [config-map debug]
  (let [{:keys [handler port database-url corpora]} config-map]
    (-> (component/system-map
         :db (new-db database-url)
         :ws (new-ws)
         :http-server (new-http-server {:port port :debug debug :components [:db :ws]}))
        (component/system-using
         {:http-server [:db :ws]
          :ws          [:db]}))))

(defn usage [options-summary]
  (->> [(str "Welcome to the command line interface of Cosycat"
             "(Collaborative Synchronized Corpus Annotation tool)")
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

(def cli-options
  [["-d" "--debug DEBUG" "run app in debug mode"]])

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn this-jar-path [& {:keys [ns] :or {ns cosycat.main}}]
  (-> (or ns (class *ns*))
      .getProtectionDomain
      .getCodeSource
      .getLocation
      io/file
      .getParentFile
      .getPath))

(defn cwd [] (System/getProperty "user.dir"))

(defn ensure-dynamic-resource-path []
  (let [resource-path (:dynamic-resource-path env)
        avatar-path (:avatar-path env)
        cwd-path (cwd)
        jar-path (this-jar-path)]
    (println (format "Starting app in [%s]"  cwd-path))
    (println (format "Jar executable located in path [%s]" jar-path))
    (when-not (= cwd-path jar-path)
      (do (println (->> ["The app isn't running in the same dir as it is located."
                         "You might result into troubles."
                         "Stopping the application now..."]
                        (string/join \newline)))
          (exit 1 "Done. Goodbye!")))
    (when-not (.exists (io/file resource-path))
      (io/make-parents (str resource-path avatar-path "dummy")))))

(defn run-server [& {:keys [debug]}]
  (let [^com.stuartsierra.component.SystemMap system (create-prod-system prod-config-map debug)]
    (ensure-dynamic-resource-path)
    (.addShutdownHook 
     (Runtime/getRuntime) 
     (Thread. (fn [] (.stop system) (shutdown-agents))))
    (.start system)))

(defn prompt-user [{:keys [prompt-msg yes-msg action]}]
  (let [rdr (java.io.BufferedReader. *in*)]
    (loop [msg (str prompt-msg " (yes/no)")]
      (println msg)
      (let [ln (.readLine rdr)]
        (condp = ln
          "yes" (do (println yes-msg)
                    (try (action)
                         (catch Exception e
                           (prn-format "Operation failed. Reason: [%s]" (.getMessage e)))))
          "no"  (println "Skipping...")
          (recur "Please enter 'yes' or 'no'"))))))

(defn clean-env []
  (let [root (clojure.java.io/file (:dynamic-resource-path env))
        db (.start (new-db (:database-url env)))]
    (prompt-user
     {:prompt-msg "Do you want to clear the '/app-resources' directory."
      :yes-msg "Clearing '/app-resources' directory..."
      :action #(delete-directory root)})
    (doseq [[k v] colls]
      (prompt-user
       {:prompt-msg (format "Do you want to drop collection [%s]" v)
        :yes-msg (format "Dropping collection [%s]..." v)
        :action #(clear-dbs db :collections [k])}))
    (.stop db)
    (exit 0 "Done. Goodbye!")))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        [action & restargs] arguments]
    (cond
      (:help options) (exit 0 (usage summary))
      (nil? action)   (exit 1 (usage summary))
      errors          (exit 1 (error-msg errors)))
    (case action
      "start" (run-server :debug (:debug options))
      "clean" (clean-env)
      (exit 1 (usage summary)))))

