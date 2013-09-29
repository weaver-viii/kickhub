(ns kickhub.github
  (:require
   [clj-http.client :as http]
   [cheshire.core :as json]))

(defn github-api-response
  "Get Github API response."
  [access-token api]
  (let [url (format "https://api.github.com%s?access_token=%s" api access-token)]
    (json/parse-string (:body (http/get url {:accept :json})) true)))

(defn github-user-repos
  "Get user github repos."
  [access-token]
  (github-api-response access-token "/user/repos"))

(defn github-user-info
  "Get user Github info."
  [access-token]
  (github-api-response access-token "/user"))

(defn render-repos-page
  "Display user repos."
  [request]
  (let [authentications
        (get-in request [:session :cemerick.friend/identity :authentications])
        access-token (:access_token (second (first authentications)))
        repos-response (github-user-repos access-token)]
    (str (vec (map :name repos-response)))))

(defn render-user-page
  "Display user infos"
  [request]
  (let [authentications
        (get-in request [:session :cemerick.friend/identity :authentications])
        access-token (:access_token (second (first authentications)))
        user-response (github-user-info access-token)]
    ;; FIXME: used for tests only so far
    (pr-str user-response)))
