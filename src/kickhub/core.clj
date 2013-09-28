(ns kickhub.core
  (:require
   [compojure.core :as compojure :refer (GET POST defroutes)]
   (compojure [handler :as handler]
              [route :as route])))

(defn- index [] "Hello!")

(defroutes app-routes
  (GET "/" [] (index))
  (route/resources "/")
  (route/not-found "Sorry, page not found."))

(def ring-handler app-routes)
