(ns kickhub.model
  (:require
   [taoensso.carmine :as car]
   [clojure.walk :refer :all]
   [simple-time.core :as time]
   [postal.core :as postal]
   [digest :as digest]
   [clojurewerkz.scrypt.core :as sc]
   [noir.session :as session]
   ;; [ring.util.response :as resp]
   ;; [clojure.string :as s]
   ;; [cheshire.core :refer :all :as json]
   ;; [clj-rss.core :as rss]
   ))

;;; * Redis connection

(def server1-conn
  {:pool {} :spec {:uri "redis://localhost:6379/"}})

(defmacro wcar* [& body]
  `(car/wcar server1-conn ~@body))

;;; * Core model functions

(defn get-username-uid
  "Given a username, return the user's uid."
  [username]
  (wcar* (car/get (str "user:" username ":uid"))))

(defn get-uid-field
  "Given a uid and a field (as a string), return the field's value."
  [uid field]
  (wcar* (car/hget (str "uid:" uid) field)))

(defn get-uid-all
  "Given a uid, return all field:value pairs."
  [uid]
  (keywordize-keys
   (wcar* (car/hgetall* (str "uid:" uid)))))

(get-uid-all "1")

(defn get-tskid-all
  "Given a tskid, return all field:value pairs."
  [tskid]
  (keywordize-keys
   (wcar* (car/hgetall* (str "tskid:" tskid)))))

;;; * Handling email

(defn send-email
  "Send an email to the `email` address with `subject` and `body`."
  [email subject body]
  (postal/send-message
   ^{:host "localhost"
     :port 25}
   {:from "Bastien <bzg@kickhub.com>"
    :to email
    :subject subject
    :body body}))

(defn- send-email-activate-account
  "Send an mail to `email` address to request account activation."
  [email authid]
  (let [subject "Welcome to Kickhub -- please activate your account"
        body (format "Welcome to Kickhub!

Here is your activation link:
%s/activate/%s

-- 
 Bastien" (System/getenv "github_client_domain") authid)]
    (send-email email subject body)))

;;; * Process paypal transactions

;; FIXME continue
(defn process-transaction
  [params]
  (send-email
   "bzg@kickhub.com" "[Kickhub] New paypal transaction"
   (pr-str params)))

;;; * User, tasks and transactions

(defn create-user
  "Create a user in the db from her username email and password."
  [username email password]
  (let [guid (wcar* (car/incr "global:uid"))
        authid (digest/md5 (str (System/currentTimeMillis) username))
        picurl (str "http://www.gravatar.com/avatar/" (digest/md5 email))]
    (wcar*
     (car/hmset
      (str "uid:" guid)
      "username" username
      "email" email
      "password" (sc/encrypt password 16384 8 1)
      "picurl" picurl
      "created" (time/format (time/now)))
     (car/mset (str "user:" username ":uid") guid
               (str "user:" email ":uid") guid
               (str "uid:" guid ":auth") authid
               (str "auth:" authid) guid)
     (car/rpush "users" guid))
    (send-email-activate-account email authid)))

(defn create-task
  "Create a new task."
  [{:keys [name license status url desc goal more]}]
  (wcar* (car/incr "global:tskid"))
  (let [tskid (wcar* (car/get "global:tskid"))
        uname (session/get :username)
        uid (get-username-uid uname)]
    (wcar*
     (car/hmset
      (str "tskid:" tskid)
      "name" name
      "license" license
      "status" status
      "url" url
      "desc" desc
      "goal" goal
      "more" more
      "updated" (time/format (time/now)))
     (car/rpush "tasks" tskid)
     (car/set (str "tskid:" tskid ":auid") uid)
     (car/sadd (str "uid:" uid ":tskid") tskid))))

(defn create-transaction
  "Create a new transaction."
  [{:keys [amount currency status tskid fromuid]}]
  (wcar* (car/incr "global:trsid"))
  (let [trsid (wcar* (car/get "global:trsid"))
        uname (session/get :username)
        uid (get-username-uid uname)]
    (wcar*
     (car/hmset
      (str "trsid:" trsid)
      "amount" amount
      "currency" currency
      "status" status
      "fromuid" fromuid
      "updated" (time/format (time/now)))
     (car/rpush "transactions" tskid)
     (car/set (str "tskid:" tskid ":auid") uid)
     (car/sadd (str "uid:" uid ":trsid") trsid))))

;;; * Handling streams

(defn get-from-stream
  "Get the n last items from a stream.
  stream is a string (e.g. \"tasks\").
  n is a positive integer."
  [stream n]
  (map #(assoc (get-tskid-all %)
          :uid (wcar* (car/get (str "tskid:" % ":auid"))))
       (reverse (take n (wcar* (car/lrange stream 0 -1))))))

(defn get-tasks [n]
  (get-from-stream "tasks" n))

(defn get-transactions [n]
  (get-from-stream "transactions" n))

;;; * Local variables

;; Local Variables:
;; eval: (orgstruct-mode 1)
;; orgstruct-heading-prefix-regexp: ";;; "
;; End:
