(defproject kickhub "0.1.0-SNAPSHOT"
  :description "KickHub: boost donations to free software/content"
  :url "http://kickhub.clojurecup.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :immutant {:context-path "/" :nrepl-port 4005}
  :dependencies
  [[org.clojure/clojure "1.5.1"]
   [com.cemerick/friend "0.1.5"]
   [friend-oauth2 "0.0.4"]
   [compojure "1.1.5"]
   [com.draines/postal "1.11.0"]
   [lib-noir "0.6.8"]
   [ring-server "0.3.0"]
   [digest "1.4.3"]
   [com.taoensso/carmine "2.2.1"]
   [enlive "1.1.4"]])
