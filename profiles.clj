{:dev {:dependencies [[com.cemerick/piggieback "0.2.1"]
                      [figwheel-sidecar "0.5.0-1"]]
       :source-paths ["src/cljs"]
       :env
       {:host "146.175.15.30"
        :database-url "mongodb://127.0.0.1:27017/cleeboTest"
        :port 3000
        :cqp {:corpora ["PYCCLE-ECCO" "DICKENS"]
              :cqp-init-file "dev-resources/cqpserver.init"}
        :blacklab
        {:corpora ["brown" "brown-id"]
         :blacklab-paths-map {"brown"    "/home/enrique/code/BlackLab/brown-index/"
                              "brown-id" "/home/enrique/code/BlackLab/brown-index-id/"}}}}
 :local {:dependencies [[com.cemerick/piggieback "0.2.1"]
                        [figwheel-sidecar "0.5.0-1"]]
         :source-paths ["src/cljs"]
         :env
         {:host "localhost"
          :database-url "mongodb://127.0.0.1:27017/cleeboTest"
          :port 3000
          :cqp {:corpora ["PYCCLE-ECCO" "DICKENS"]
                :cqp-init-file "dev-resources/cqpserver.init"}
          :blacklab
          {:corpora ["brown" "brown-id"]
           :blacklab-paths-map {"brown"    "/home/enrique/code/BlackLab/brown-index/"
                                "brown-id" "/home/enrique/code/BlackLab/brown-index-id/"}}}}}
