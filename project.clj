(defproject kickhub "0.0.1-SNAPSHOT"
  :description "KickHub: donate to free culture"
  :url "http://kickhub.com"
  :license {:name "GNU Affero General Public License version 3"
            :url "http://www.gnu.org/licenses/agpl.txt"
            :repo "https://github.com/bzg/kickhub"}
  :codox {:sources ["src/kickhub"]}
  :dependencies
  [[org.clojure/clojure "1.5.1"]
   [com.cemerick/friend "0.2.0" :exclusions [org.apache.httpcomponents/httpclient org.clojure/core.cache]]
   [org.clojure/data.json "0.2.4"]
   [friend-oauth2 "0.1.1"]
   [garden "1.1.6"]
   [prismatic/schema "0.2.2"]
   [simple-time "0.1.1"]
   [cheshire "5.3.1"]
   [compojure "1.1.6"]
   [com.draines/postal "1.11.1"]
   [lib-noir "0.8.2"]
   [clojurewerkz/scrypt "1.1.0"]
   [http-kit "2.1.18"]
   [clj-rss "0.1.6"]
   [clj-http "0.9.1"]
   [digest "1.4.4"]
   [com.taoensso/carmine "2.6.2"]
   [enlive "1.1.5"]]
  :main kickhub.handler
  :description "kickhub.com")
