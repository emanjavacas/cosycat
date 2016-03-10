{:dev {:dependencies [[com.cemerick/piggieback "0.2.1"]
                      [figwheel-sidecar "0.5.0-1"]]
       :source-paths ["src/cljs" "src/clj" "src/cljc"]
       :env
       {:host "146.175.15.30"
        :database-url "mongodb://127.0.0.1:27017/cleeboTest"
        :port 3000
        :cqp {:corpora ["PYCCLE-ECCO" "DICKENS"]
              :cqp-init-file "dev-resources/cqpserver.init"}
        :blacklab
        {:corpora [;"brown-id"
                   "shc"]
         :blacklab-paths-map
         {;"brown-id" "/home/enrique/code/BlackLab/brown-index-id/"
          "shc"      "/home/enrique/code/BlackLab/shc/"}}}}
 :local {:dependencies [[com.cemerick/piggieback "0.2.1"]
                        [figwheel-sidecar "0.5.0-1"]]
         :source-paths ["src/cljs" "src/cljc" "src/clj"]
         :env
         {:host "localhost"
          :database-url "mongodb://127.0.0.1:27017/cleeboTest"
          :port 3000
          :cqp {:corpora ["DICKENS"]
                :cqp-init-file "dev-resources/cqpserver.init"}
          :blacklab
          {:corpora ["brown-id" ;"shc"
                     ]
           :blacklab-paths-map
           {"brown-id" "/home/enrique/code/BlackLab/brown-index-id/"
;            "shc" "/home/enrique/code/BlackLab/shc/"
            }}}}}
