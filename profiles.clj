{:dev {:dependencies [[com.cemerick/piggieback "0.2.1"] [figwheel-sidecar "0.5.2"]]
       :source-paths ["env/dev/clj"]
       :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]}}}
       :env {:host "localhost"
             :dev? true
             :admin "enrique"
             :log-dir "/home/manjavacas/code/cosycat/dev-resources"
             :database-url "mongodb://127.0.0.1:27017/cosycatDev"
             :port 3000
             :session-expires 900       ;in minutes
             :tagset-paths ["/home/manjavacas/code/cosycat/resources/public/tagsets"]
             :corpora
             [{:corpus "brown"
               :type :blacklab-server
               :args {:server "localhost:8080"
                      :web-service "blacklab-server-1.6.0-SNAPSHOT"}}]}}
 :test {:env {:database-url "mongodb://127.0.0.1:27017/cosycatTest"
              :dev? true
              :corpora
              [{:corpus "mbg-small"
                :type :blacklab-server
                :args {:server "mbgserver.uantwerpen.be:8080"
                       :web-service "blacklab-server-1.4-SNAPSHOT"}}]}}}
