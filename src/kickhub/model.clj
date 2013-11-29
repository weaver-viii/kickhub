(ns kickhub.model
  (:require
   [digest :as digest]
   [clojure.walk :refer :all]
   [postal.core :as postal]
   [clojurewerkz.scrypt.core :as sc]
   [ring.util.response :as resp]
   [taoensso.carmine :as car]))

(def server1-conn
  {:pool {} :spec {:uri "redis://localhost:6379/"}})

(defmacro wcar* [& body]
  `(car/wcar server1-conn ~@body))

;;; * General

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

(defn- uid-admin-of-pid? [uid pid]
  (= (wcar* (car/get (str "pid:" pid ":auid"))) uid))

(defmacro keywordize-array-mapize [& body]
  `(keywordize-keys
    (apply array-map ~@body)))

(defn get-uid-projects [uid]
  (map #(keywordize-array-mapize
         (wcar* (car/hgetall (str "pid:" %))))
       (wcar* (car/smembers (str "uid:" uid ":apid")))))

(defn get-uid-transactions [uid]
  (map #(keywordize-array-mapize
         (wcar* (car/hgetall (str "tid:" %))))
       (wcar* (car/smembers (str "uid:" uid ":atid")))))

;; (get-uid-transactions "1")
;; (get-uid-projects (get-username-uid "bzg"))

;;; * Email

(defn send-email [email subject body]
  (postal/send-message
   ^{:host "localhost"
     :port 25}
   {:from "Bastien <bzg@bzg.fr>"
    :to email
    :subject subject
    :body body}))

(defn send-email-activate-account [email authid]
  (let [subject "Welcome to Kickhub -- please activate your account"
        body (format "Welcome to Kickhub!

Here is your activation link:
http://localhost:8080/activate/%s

-- 
 Bastien" authid)]
    (send-email email subject body)))
    
(defn send-email-subscribe-mailing [subscriber-email]
  (let [subject "New subscriber for Kickhub"]
    (send-email "bzg@bzg.fr" subject subscriber-email)))

;; FIXME: use repo?
(defn send-email-new-project [name repo uid]
  (let [subject "Thanks for your new project!"
        project_url (format "http://localhost:8080/project/%s" name)]
    (send-email (get-uid-field uid "e")
                subject
                project_url)))

;; FIXME use tid?
(defn send-email-new-transaction [amount pid uid fuid tid authid]
  (let [from_user (get-uid-field uid "u")
        subject (format "You received some support from %s" from_user)
        from_email (get-uid-field uid "e")
        for_project (get-pid-field pid "name")
        project_url (format "http://localhost:8080/project/%s" for_project)
        user_url (format "http://localhost:8080/user/%s" from_user)
        confirm_url (format "http://localhost:8080/confirm/%s" authid)]
    (send-email from_email
                subject
                (str project_url "\n"
                     user_url
                     confirm_url))))

(defn email-to-mailing [{:keys [email]}]
  (do (wcar* (car/sadd "mailing" email))
      (send-email-subscribe-mailing email)
      (resp/redirect (str "/?m=" email))))

;;; * Create users

(defn create-user [username email password]
  (let [guid (wcar* (car/incr "global:uid"))
        authid (digest/md5 (str (System/currentTimeMillis) username))
        picurl (str "http://www.gravatar.com/avatar/" (digest/md5 email))]
    (do (wcar*
         (car/hmset
          (str "uid:" guid)
          "u" username
          "e" email
          "p" (sc/encrypt password 16384 8 1)
          "picurl" picurl
          "created" (java.util.Date.))
         (car/mset (str "user:" username ":uid") guid
                   (str "uid:" guid ":auth") authid
                   (str "auth:" authid) guid)
         (car/rpush "users" guid))
        (send-email-activate-account email authid)
        (resp/redirect "/"))))

(defn register-user
  "Register a new user"
  [{:keys [username email password]}]
  (do (create-user username email password)
      (resp/redirect "/")))

(defn activate-user [authid]
  (let [guid (wcar* (car/get (str "auth:" authid)))]
    (wcar* (car/hset (str "uid:" guid) "active" 1))))

(defn confirm-transaction [authid]
  (let [tid (wcar* (car/get (str "auth:" authid)))]
    (wcar* (car/hset (str "tid:" tid) "confirmed" "1"))))

(defn- project-by-uid-exists? [name uid]
  (uid-admin-of-pid? uid (get-pname-pid name)))

(defn create-project
  "Create a new project."
  [name repo uid]
  (when-not (project-by-uid-exists? name uid)
    (let [pid (wcar* (car/incr "global:pid"))]
      ;; Create the project in the db
      (wcar* (car/hmset
              (str "pid:" pid)
              "name" name
              "repo" repo
              "created" (java.util.Date.)
              "by" uid)
             (car/rpush "projects" pid)
             (car/mset (str "pid:" pid ":auid") uid
                       (str "project:" name ":pid") pid)
             (car/sadd (str "uid:" uid ":apid") pid))
      ;; Send an email to the author of the project
      (send-email-new-project name repo uid))))

(defn create-transaction
  "Create a new transaction."
  [amount pid uid fuid]
  (let [tid (wcar* (car/incr "global:tid"))
        authid (digest/md5 (str (System/currentTimeMillis) tid))]
    ;; Create the transaction in the db
    (wcar* (car/hmset
            (str "tid:" tid)
            "created" (java.util.Date.)
            "by" fuid
            "to" uid
            "for" pid
            "amount" amount
            "confirmed" "0")
           (car/rpush "trans" tid)
           (car/mset (str "tid:" tid ":auid") fuid
                     (str "tid:" tid ":auth") authid
                     (str "auth:" authid) tid)
           (car/sadd (str "uid:" fuid ":atid") tid))
    ;; Send emails to the recipient of the transaction
    (send-email-new-transaction amount pid uid fuid tid authid)))

;; (defn filter-out-active-repos
;;   "Filter out repos that user uid has already activated"
;;   [repos uid]
;;   (filter #(not (=
;;             (wcar* (car/sismember
;;                     (str "uid:" uid ":apid")
;;                     (get-pname-pid (:name %))))
;;             1))
;;           repos))

;; (defn get-last-stream [[kind id] count]
;;   (let [plist (reverse (take count (wcar* (car/lrange kind 0 -1))))]
;;     (map #(keywordize-array-mapize
;;            (wcar* (car/hgetall (str id %))))
;;          plist)))

;;; 

;; Local Variables:
;; eval: (orgstruct-mode 1)
;; orgstruct-heading-prefix-regexp: ";;; "
;; End:
