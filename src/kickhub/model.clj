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

;; FIXME: Not used yet
;; (defn get-pid-field
;;   "Given a pid and a field (as a string), return the info."
;;   [pid field]
;;   (wcar* (car/hget (str "pid:" pid) field)))

;; FIXME: Not used yet
;; (defn uid-admin-of-pid? [uid pid]
;;   (= (wcar* (car/get (str "pid:" pid ":auid"))) uid))

(defn get-last-projects [count]
  (let [plist (wcar* (car/lrange "timeline" 0 (- count)))]
    (map #(wcar* (car/hgetall (str "pid:" %))) plist)))

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

(defn create-project
  "Create a new project."
  [repo uid]
  (let [pid (wcar* (car/incr "global:pid"))]
    (wcar* (car/hmset
            (str "pid:" pid)
            "name" repo "created" (java.util.Date.))
           (car/rpush "timeline" pid)
           (car/set (str "pid:" pid ":auid") uid)
           (car/sadd (str "uid:" uid ":apid") pid))))
