(ns kickhub.model
  (:require
   [digest :as digest]
   [clojure.walk :refer :all]
   [postal.core :as postal]
   [clojurewerkz.scrypt.core :as sc]
   [ring.util.response :as resp]
   [taoensso.carmine :as car]))

;;; * Carmine connection and macro

(def server-connection
  ^{:doc "Redis serveur connection info" :private true}
  {:pool {} :spec {:uri "redis://localhost:6379/"}})

(defmacro wcar* "Macro to process body within a redis server connection."
  [& body]
  `(car/wcar server-connection ~@body))

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

(defn get-tid-all
  "Given a tid, return all info about it."
  [uid]
  (wcar* (car/hgetall (str "tid:" uid))))

(defn get-tid-field
  "Given a tid and a field (as a string), return the info."
  [uid field]
  (wcar* (car/hget (str "tid:" uid) field)))

(defn- uid-admin-of-pid? [uid pid]
  (= (wcar* (car/get (str "pid:" pid ":auid"))) uid))

(defmacro keywordize-array-mapize [& body]
  `(keywordize-keys
    (apply array-map ~@body)))

(defn get-uid-projects
  "Get the list of projects for user `uid`."
  [uid]
  (map #(keywordize-array-mapize
         (wcar* (car/hgetall (str "pid:" %))))
       (wcar* (car/smembers (str "uid:" uid ":apid")))))

(defn get-uid-transactions
  "Get the list of transaction for user `uid`."
  [uid]
  (map #(keywordize-array-mapize
         (wcar* (car/hgetall (str "tid:" %))))
       (wcar* (car/smembers (str "uid:" uid ":atid")))))

;;; * Email processing

;; FIXME: This uses bzg@bzg.fr as the value of the From: header
;; We may store the From: header's value in a variable.
(defn- send-email
  "Send an email to the `email` address with `subject` and `body`."
  [email subject body]
  (postal/send-message
   ^{:host "localhost"
     :port 25}
   {:from "Bastien <bzg@bzg.fr>"
    :to email
    :subject subject
    :body body}))

;; FIXME: Store email messages into the databas
(defn- send-email-activate-account
  "Send an mail to `email` address to request account activation."
  [email authid]
  (let [subject "Welcome to Kickhub -- please activate your account"
        body (format "Welcome to Kickhub!

Here is your activation link:
http://localhost:8080/activate/%s

-- 
 Bastien" authid)]
    (send-email email subject body)))
    
(defn send-email-subscribe-mailing
  "Send an mail to `subscriber-email` to notify bzg@bzg.fr of new subscribers."
  [subscriber-email]
  (let [subject "New subscriber for Kickhub"]
    (send-email "bzg@bzg.fr" subject subscriber-email)))

;; FIXME: use repo?
(defn- send-email-new-project
  "Send an email notification to `name` for a new project."
  [name repo uid]
  (let [subject "Thanks for your new project!"
        project_url (format "http://localhost:8080/project/%s" name)]
    (send-email (get-uid-field uid "e")
                subject
                project_url)))

;; FIXME use tid?
(defn- send-email-new-transaction
  "Send an email from uid email to fuid email to notify new transaction."
  [amount pid uid fuid tid authid]
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

(defn email-to-mailing
  "Add email to the `mailing` key in the redis db."
  [{:keys [email]}]
  (do (wcar* (car/sadd "mailing" email))
      (send-email-subscribe-mailing email)
      (resp/redirect (str "/?m=" email))))

;;; * Handle news

(defn- create-news
  "Create a news.
News can be of type:
- new user         (\"u\")
- new project      (\"p\")
- new transaction  (\"t\")
- new confirmation (\"tc\")"
  [{:keys [type fuid uid tid pid]}]
  (let [gnid (wcar* (car/incr "global:nid"))
        ndate (System/currentTimeMillis)]
    (wcar*
     (car/hmset
      (str "nid:" gnid) "t" type "uid" uid "tid" tid "pid" pid
      "date" ndate))))

(defn- news-to-sentence
  "Given a news id `nid`, make the news human-readable."
  [nid]
  (let [nparams
        (keywordize-array-mapize
         (wcar* (car/hgetall (str "nid:" "1"))))]
    (condp = (:t nparams)
      "u"  (str "Welcome to " (get-uid-field (:fuid nparams) "u") "!")
      "p"  (str "New project "
                (get-pid-field (:pid nparams) "name") "by"
                (get-pid-field (:pid nparams) "by"))
      "t"  (format "New transaction from %s to %s for %s"
                   (get-username-uid (get-tid-field (:tid nparams) "by"))
                   (get-username-uid (get-tid-field (:tid nparams) "to"))
                   (get-pid-field (get-tid-field (:tid nparams) "for") "name"))
      "tc" (format "%s confirmed he the transaction from %s for %s"
                   (get-username-uid (get-tid-field (:tid nparams) "to"))
                   (get-username-uid (get-tid-field (:tid nparams) "by"))
                   (get-pid-field (get-tid-field (:tid nparams) "for") "name")))))

;;; * Handle transactions

(defn confirm-transaction
  "Confirm transaction `authid` in the db."
  [authid]
  (let [tid (wcar* (car/get (str "auth:" authid)))]
    (wcar* (car/hset (str "tid:" tid) "confirmed" "1"))
    ;; Create a news about it
    (create-news {:type "tc" :tid tid})))

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
    ;; Create a news about it
    (create-news {:type "t" :tid tid})
    ;; Send emails to the recipient of the transaction
    (send-email-new-transaction amount pid uid fuid tid authid)))
;;; * Handle users

(defn create-user
  "Create a user in the db from her username email and password."
  [username email password]
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
        ;; Create a news about it
        (create-news {:type "u" :uid guid})
        (send-email-activate-account email authid)
        (resp/redirect "/"))))

(defn register-user
  "Register a new user in the db."
  [{:keys [username email password]}]
  (do (create-user username email password)
      (resp/redirect "/")))

(defn activate-user
  "Activate a new user in the db."
  [authid]
  (let [guid (wcar* (car/get (str "auth:" authid)))]
    (wcar* (car/hset (str "uid:" guid) "active" 1))))

;;; * Handle projects

(defn- project-by-uid-exists?
  "Does project `pname` belongs to user `uid`?"
  [pname uid]
  (uid-admin-of-pid? uid (get-pname-pid pname)))

(defn create-project
  "Create a new project."
  [pname repo uid]
  (when-not (project-by-uid-exists? pname uid)
    (let [pid (wcar* (car/incr "global:pid"))]
      ;; Create the project in the db
      (wcar* (car/hmset
              (str "pid:" pid)
              "name" pname
              "repo" repo
              "created" (java.util.Date.)
              "by" uid)
             (car/rpush "projects" pid)
             (car/mset (str "pid:" pid ":auid") uid
                       (str "project:" pname ":pid") pid)
             (car/sadd (str "uid:" uid ":apid") pid))
      ;; Create a news about it
      (create-news {:type "p" :pid pid})
      ;; Send an email to the author of the project
      (send-email-new-project pname repo uid))))

;;; * Old code and tests

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

;; (get-uid-transactions "1")
;; (get-uid-projects (get-username-uid "bzg"))

;;; * Local variables

;; Local Variables:
;; eval: (orgstruct-mode 1)
;; orgstruct-heading-prefix-regexp: ";;; "
;; End:
