(defproject kickhub "0.1.1-SNAPSHOT"
  :description "KickHub: boost donations to free software/content"
  :url "http://kickhub.clojurecup.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :immutant {:context-path "/" :nrepl-port 4005}
  :dependencies
  [[org.clojure/clojure "1.5.1"]
   [com.cemerick/friend "0.2.0" :exclusions [org.clojure/core.cache]]
   [friend-oauth2 "0.1.1"]
   [compojure "1.1.6"]
   [com.draines/postal "1.11.1"]
   [lib-noir "0.7.6"]
   [clojurewerkz/scrypt "1.0.0"]
   [ring-server "0.3.1"]
   [digest "1.4.3"]
   [com.taoensso/carmine "2.4.0"]
   [enlive "1.1.4"]])
