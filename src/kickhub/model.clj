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

(defn logged-in-normally? [req]
  (let [username (get-in req [:session :cemerick.friend/identity :current])]
    (not (empty? (wcar* (car/get (str "user:" username ":uid")))))))

(defn get-username-uid
  "Given her username, return the user's uid."
  [username]
  (wcar* (car/get (str "user:" username ":uid"))))

(defn get-uid-field
  "Given a uid and a field (as a string), return the info."
  [uid field]
  (wcar* (car/hget (str "uid:" uid) field)))

(defn activate-user [authid]
  (let [guid (wcar* (car/get (str "auth:" authid)))]
    (wcar* (car/hset (str "uid:" guid) "active" 1)))
  (h/html5
   [:body
    [:h1 "You are now an active user."]]))

(defn send-email [email activation-link]
  (postal/send-message
   ^{:host "localhost"
     :port 25}
   {:from "Bastien <bzg@bzg.fr>"
    :to email
    :subject "Thanks for testing KickHub!"
    :body (str "Please click on the link below to activate your account:\n"
               activation-link)})
  "Email sent!")

(defn create-user [username email password isactive nosend]
  (let [guid (wcar* (car/incr "global:uid"))
        authid (digest/md5 (str (System/currentTimeMillis) username))]
       (wcar*
        (car/hmset
         (str "uid:" guid)
         "u" username "p" password "e" email
         "pic" (format "<img src=\"http://www.gravatar.com/avatar/%s\" />"
                       (digest/md5 email))
         "updated" (java.util.Date.)
         "active" (if isactive 1 0))
        (car/set (str "uid:" guid ":auth") authid)
        (car/set (str "auth:" authid) guid)
        (car/set (str "user:" username ":uid") guid)
        (car/rpush "users" guid))
       (if-not nosend
         (send-email email (str (System/getenv "github_client_domain") "/activate/" authid)))
       (str "Registered!  Thanks.<br/>"
            (format "<img src=\"http://www.gravatar.com/avatar/%s\" />"
                    (digest/md5 email)))))
