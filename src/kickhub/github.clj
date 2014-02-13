(ns kickhub.github
  (:require [cheshire.core :as json]
            [clj-http.client :as http]))

(defn- github-api-response
  "Get Github API response."
  [access-token api]
  (let [url (format "https://api.github.com%s?access_token=%s" api access-token)]
    (json/parse-string (:body (http/get url {:accept :json})) true)))

(defn github-user-repos
  "Get user GitHub repos."
  [access-token]
  (github-api-response access-token "/user/repos"))

(defn github-user-info
  "Get user GitHub info."
  [access-token]
  (github-api-response access-token "/user"))

(defn github-user-basic-info
  "Get user GitHub basic info."
  [access-token]
  (let [infos (github-api-response access-token "/user")]
    (assoc {}
      :username (:login infos)
      :email (:email infos)
      :picurl (str "http://www.gravatar.com/avatar/" (:gravatar_id infos)))))

(defn render-repos-page
  "Display user repos."
  [request]
  (let [access-token (get-in request [:session :cemerick.friend/identity :current])
        repos-response (github-user-repos access-token)]
    (str (vec (map :name repos-response)))))

(defn render-user-page
  "Display user infos"
  [request]
  (let [access-token (get-in request [:session :cemerick.friend/identity :current])
        user-response (github-user-info access-token)]
    ;; FIXME: used for tests only so far
    (pr-str user-response)))
