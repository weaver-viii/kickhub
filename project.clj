(defproject kickhub "0.1.1-SNAPSHOT"
  :description "KickHub: boost donations to free softwares"
  :url "http://kickhub.com"
  :license {:name "GNU Affero General Public License version 3"
            :url "http://www.gnu.org/licenses/agpl.txt"
            :repo "https://github.com/bzg/kickhub"}
  :codox {:sources ["src/kickhub"]}
  ;; :immutant {:context-path "/" :nrepl-port 4005}
  :dependencies
  [[org.clojure/clojure "1.5.1"]
   [com.cemerick/friend "0.2.0" :exclusions [org.clojure/core.cache]]
   [org.clojure/data.json "0.2.4"]
   [friend-oauth2 "0.1.1"]
   [garden "1.1.5"]
   [cheshire "5.3.1"]
   [compojure "1.1.6"]
   [com.draines/postal "1.11.1"]
   [lib-noir "0.8.0"]
   [clojurewerkz/scrypt "1.1.0"]
   [clj-rss "0.1.3"]
   [ring-server "0.3.1" :exclusions [ring]]
   [digest "1.4.3"]
   [com.taoensso/carmine "2.4.6"]
   [enlive "1.1.5"]])
