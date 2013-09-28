(defproject kickhub "0.1.0-SNAPSHOT"
  :description "KickHub: boost donations to free software/content"
  :url "http://kickhub.clojurecup.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :immutant {:context-path "/"}
  :dependencies
  [[org.clojure/clojure "1.5.1"]
   [com.cemerick/friend "0.1.5"]
   [compojure "1.1.5"]
   [com.draines/postal "1.10.3"]
   [lib-noir "0.6.6"]])
