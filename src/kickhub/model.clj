(ns kickhub.model
  (:require
   [digest :as digest]
   [clojure.walk :refer :all]
   [hiccup.page :as h]
   [hiccup.element :as e]
   [postal.core :as postal]
   [ring.util.response :as resp]
   [taoensso.carmine :as car]))

(def server1-conn
  {:pool {} :spec {:uri "redis://localhost:6379/"}})

(defmacro wcar* [& body]
  `(car/wcar server1-conn ~@body))

(defn send-email [email subject body]
  (postal/send-message
   ^{:host "localhost"
     :port 25}
   {:from "Bastien <bzg@bzg.fr>"
    :to email
    :subject subject
    :body body}))

(defn send-email-activate-account [email]
  (let [subject "Welcome to Kickhub -- please activate your account"
        body "Welcome to Kickhub!\n\nHere is your activation link:\n\n-- \nBastien"]
    (send-email email subject body)))
    
(defn send-email-subscribe-mailing [email]
  (let [subject "New subscriber for Kickhub"]
    (send-email "bzg@bzg.fr" subject email)))

(defn email-to-mailing [{:keys [email]}]
  (do (wcar* (car/sadd "mailing" email))
      (send-email-subscribe-mailing email)
      (resp/redirect (str "/?m=" email))))

(defn get-username-uid
  "Given a username, return the corresponding uid."
  [username]
  (wcar* (car/get (str "user:" username ":uid"))))

(defn get-pname-pid
  "Given a project's name, return the corresponding pid."
  [pname]
  (wcar* (car/get (str "project:" pname ":pid"))))

(defn get-uid-all
  "Given a uid, return all info about her."
  [uid]
  (wcar* (car/hgetall (str "uid:" uid))))

(defn get-uid-field
  "Given a uid and a field (as a string), return the info."
  [uid field]
  (wcar* (car/hget (str "uid:" uid) field)))

(defn get-pid-all
  "Given a pid, return all info about it."
  [pid]
  (wcar* (car/hgetall (str "pid:" pid))))

(defn get-pid-field
  "Given a pid and a field (as a string), return the info."
  [pid field]
  (wcar* (car/hget (str "pid:" pid) field)))

(defn filter-out-active-repos
  "Filter out repos that user uid has already activated"
  [repos uid]
  (filter #(not (=
            (wcar* (car/sismember
                    (str "uid:" uid ":apid")
                    (get-pname-pid (:name %))))
            1))
          repos))

(defn- uid-admin-of-pid? [uid pid]
  (= (wcar* (car/get (str "pid:" pid ":auid"))) uid))

(defmacro keywordize-array-mapize [& body]
  `(keywordize-keys
    (apply array-map ~@body)))

(defn get-last-stream [[kind id] count]
  (let [plist (reverse (take count (wcar* (car/lrange kind 0 -1))))]
    (map #(keywordize-array-mapize
           (wcar* (car/hgetall (str id %))))
         plist)))

(defn get-uid-projects [uid]
  (map #(keywordize-array-mapize
         (wcar* (car/hgetall (str "pid:" %))))
       (wcar* (car/smembers (str "uid:" uid ":apid")))))

(defn create-user [username email password]
  (let [guid (wcar* (car/incr "global:uid"))]
    (do (wcar*
         (car/hmset
          (str "uid:" guid)
          "u" username
          "e" email
          "p" password
          "picurl" (str "http://www.gravatar.com/avatar/"
                        (digest/md5 email))
          "created" (java.util.Date.))
         (car/set (str "user:" username ":uid") guid)
         (car/rpush "users" guid))
        (send-email-activate-account email)
        (resp/redirect "/"))))

(defn- project-by-uid-exists? [repo uid]
  (uid-admin-of-pid? uid (get-pname-pid repo)))

(defn create-project
  "Create a new project."
  [repo uid]
  (when-not (project-by-uid-exists? repo uid)
    (let [pid (wcar* (car/incr "global:pid"))]
      (wcar* (car/hmset
              (str "pid:" pid)
              "name" repo "created" (java.util.Date.)
              "by" uid)
             (car/rpush "projects" pid)
             (car/set (str "pid:" pid ":auid") uid)
             (car/sadd (str "uid:" uid ":apid") pid)
             (car/set (str "project:" repo ":pid") pid)))))

(defn create-transaction
  "Create a new project."
  [amount pid uid fuid]
  (let [tid (wcar* (car/incr "global:tid"))]
    (wcar* (car/hmset
            (str "tid:" tid)
            "created" (java.util.Date.)
            "by" fuid
            "to" uid
            "for" pid
            "amount" amount
            "confirmed" "0")
           (car/rpush "trans" tid)
           (car/set (str "tid:" tid ":auid") fuid))))
