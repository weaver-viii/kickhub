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

;; (defn set-uid-field
;;   "Given a uid, a field (as a string) and value, set the field's value."
;;   [uid field value]
;;   (wcar* (car/hset (str "uid:" uid) field value)))

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
         "Learn more about the *why and the *how*:\n"
         "http://bzg.fr/clojurecup-2013-the-problem.html\n"
         "http://kickhub.clojurecup.com\n\n"
         "... and don't forget to vote for us!\n"
         "http://clojurecup.com/app.html?app=kickhub\n\n-- \nBastien")}))

(defn create-user [username email]
  (let [guid (wcar* (car/incr "global:uid"))]
    (do (wcar*
         (car/hmset
          (str "uid:" guid)
          "u" username "e" email
          "picurl" (str "http://www.gravatar.com/avatar/"
                        (digest/md5 email))
          "created" (java.util.Date.))
         (car/set (str "user:" username ":uid") guid)
         (car/rpush "users" guid))
        (send-email email))))
