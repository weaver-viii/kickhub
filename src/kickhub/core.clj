(ns kickhub.core
  (:require
   [compojure.core :as compojure :refer (GET POST defroutes)]
   (compojure [handler :as handler]
              [route :as route])
   [hiccup.page :as h]
   [postal.core :as postal]))

(defn- index []
  (h/html5
   (h/include-css "/css/kickhub.css")
   [:body "Hello!"]))

(defn- send-email [user]
  (postal/send-message
   ^{;; :user login
     ;; :pass password
     :host "localhost"
     :port 25}
   {:from "Bastien <bzg@bzg.fr>"
    :to user
    :subject "Thanks for testing KickHub!"
    :body "Hopefully this message will be more interesting soon."})
  "Email sent.")

(defroutes app-routes
  (GET "/" [] (index))
  (GET "/sendmail/:user" [user] (send-email user))
  (route/resources "/")
  (route/not-found "Sorry, page not found."))

(def ring-handler app-routes)
