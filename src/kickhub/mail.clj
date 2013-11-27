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

(defn send-email-activate-account [email]
  (let [subject "Welcome to Kickhub -- please activate your account"
        body "Welcome to Kickhub!\n\nHere is your activation link:\n\n-- \nBastien"]
    (send-email email subject body)))
    
(defn send-email-subscribe-mailing [email]
  (let [subject "New subscriber for Kickhub"]
    (send-email "bzg@bzg.fr" subject email)))
