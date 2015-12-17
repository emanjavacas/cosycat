{:dev {:dependencies [[com.cemerick/piggieback "0.2.1"]
                      [figwheel-sidecar "0.5.0-1"]]
       :source-paths ["src/cljs"]
       :env {:database-url "mongodb://127.0.0.1:27017/mbgBack"
             :port 3000
             :cqp-init-file "dev-resources/cqpserver.init"}}}
