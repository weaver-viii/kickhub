(ns kickhub.model
  (:require
   [digest :as digest]
   [hiccup.page :as h]
   [hiccup.element :as e]
   [postal.core :as postal]
   [taoensso.carmine :as car]))

(def server1-conn
  {:pool {} :spec {:uri "redis://localhost:6379/"}})

(defmacro wcar* [& body]
  `(car/wcar server1-conn ~@body))

(defn get-username-uid
  "Given her username, return the user's uid."
  [username]
  (wcar* (car/get (str "user:" username ":uid"))))

(defn get-uid-field
  "Given a uid and a field (as a string), return the info."
  [uid field]
  (wcar* (car/hget (str "uid:" uid) field)))

(defn send-email [email]
  (postal/send-message
   ^{:host "localhost"
     :port 25}
   {:from "Bastien <bzg@bzg.fr>"
    :to email
    :subject "Thanks for testing KickHub!"
    :body
    (str "Welcome to KickHub!\n\n"
         "KickHub aims at boosting donations to free software/content.\n\n"
         "Learn more about KickHub:\n"
         "http://kickhub.clojurecup.com\n"
         "http://bzg.fr/clojurecup-2013-the-problem.html\n\n"
         "... and don't forget to vote for us!\n"
         "http://clojurecup.com/app.html?app=kickhub\n\n-- \nBastien")}))

(defn create-user [username email]
  (let [guid (wcar* (car/incr "global:uid"))]
    (do (wcar*
         (car/hmset
          (str "uid:" guid)
          "u" username "e" email
          "pic" (format "<img src=\"http://www.gravatar.com/avatar/%s\" />"
                        (digest/md5 email))
          "created" (java.util.Date.))
         (car/set (str "user:" username ":uid") guid)
         (car/rpush "users" guid))
        (send-email email))))
