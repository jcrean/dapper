(defproject web-auth "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.2.0"]
         	 [org.clojure/clojure-contrib "1.2.0"]
                 [dapper "1.0.0-SNAPSHOT"]
                 [compojure/compojure "0.6.5"]
                 [sandbar/sandbar "0.3.0"]]
  :dev-dependencies [[swank-clojure "1.2.0"]
                     [lein-ring  "0.4.5"]
                     [ring/ring-jetty-adapter "0.3.11"]]
  :ring {:handler web-auth.core/app})
