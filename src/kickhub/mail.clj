(ns kickhub.mail
  (:require
   [postal.core :as postal]
   [taoensso.carmine :as car]))

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
    
(defn send-email-subscribe-mailing [email]
  (let [subject "New subscriber for Kickhub"]
    (send-email "bzg@bzg.fr" subject email)))
