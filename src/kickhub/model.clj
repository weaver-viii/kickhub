(ns kickhub.model
  (:require
   [digest :as digest]
   [clojure.walk :refer :all]
   [hiccup.page :as h]
   [hiccup.element :as e]
   [postal.core :as postal]
   [taoensso.carmine :as car]))

(def server1-conn
  {:pool {} :spec {:uri "redis://localhost:6379/"}})

(defmacro wcar* [& body]
  `(car/wcar server1-conn ~@body))

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
         "Learn more about the why and the how:\n"
         "http://bzg.fr/clojurecup-2013-the-problem.html\n"
         "http://kickhub.clojurecup.com\n\n"
         "... and don't forget to vote for this project :)\n"
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
