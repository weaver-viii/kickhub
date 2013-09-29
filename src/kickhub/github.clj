(ns kickhub.github
  (:require
   [clj-http.client :as http]
   [cheshire.core :as json]))

;; FIXME: refactoring needed

(defn get-github-repos
  "Github API call for the current authenticated users
repository list."
  [access-token]
  (let [url (str "https://api.github.com/user/repos?access_token=" access-token)
        response (http/get url {:accept :json})
        repos (json/parse-string (:body response) true)]
    repos))

(defn get-github-emails
  [access-token]
  (let [url (str "https://api.github.com/user/emails?access_token=" access-token)
        response (http/get url {:accept :json})
        emails (json/parse-string (:body response) true)]
    emails))

(defn get-github-user
  [access-token]
  (let [url (str "https://api.github.com/user?access_token=" access-token)
        response (http/get url {:accept :json})
        user (json/parse-string (:body response) true)]
    user))

(defn render-repos-page
  "Show a list of the current users github repositories.
Do this by calling the github api with the OAuth2 access token
that the friend authentication has retrieved."
  [request]
  (let [authentications
        (get-in request [:session :cemerick.friend/identity :authentications])
        access-token (:access_token (second (first authentications)))
        repos-response (get-github-repos access-token)]
    (str (vec (map :name repos-response)))))

(defn render-user-page
  "Show a list of the current users github repositories.
Do this by calling the github api with the OAuth2 access token
that the friend authentication has retrieved."
  [request]
  (let [authentications
        (get-in request [:session :cemerick.friend/identity :authentications])
        access-token (:access_token (second (first authentications)))
        user-response (get-github-user access-token)]
    ;; FIXME: used for tests only so far
    (pr-str user-response)))

(defn render-emails-page
  "Show a list of the current users github repositories.
Do this by calling the github api with the OAuth2 access token
that the friend authentication has retrieved."
  [request]
  (let [authentications
        (get-in request [:session :cemerick.friend/identity :authentications])
        access-token (:access_token (second (first authentications)))
        emails-response (get-github-emails access-token)]
    ;; (str (vec (map :name emails-response)))))
    (pr-str emails-response)))
