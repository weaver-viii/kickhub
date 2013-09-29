(ns kickhub.core
  (:require
   [noir.util.middleware :as middleware]
   [ring.util.response :as resp]
   [kickhub.model :refer :all]
   [kickhub.github :refer :all]
   [kickhub.html.templates :refer :all]
   [ring.util.codec :as codec]
   [compojure.core :as compojure :refer (GET POST defroutes)]
   (compojure [handler :as handler]
              [route :as route])
   [hiccup.page :as h]
   [hiccup.element :as e]
   [cemerick.friend :as friend]
   (cemerick.friend [workflows :as workflows]
                    [credentials :as creds])
   [cemerick.friend.credentials :refer (hash-bcrypt)]
   [friend-oauth2.workflow :as oauth2]))

(defn- authenticated? [req]
  (get-in req [:session :cemerick.friend/identity]))

(defn index [req]
  (if (authenticated? req)
    (let [authentications
          (get-in req [:session :cemerick.friend/identity :authentications])
          access-token (:access_token (second (first authentications)))
          basic (github-user-info access-token)
          username (:login basic)
          email (:email basic)
          uid (get-username-uid username)]
      (when (empty? uid) (create-user username email))
      (indextpl {:logged "Log out" :link "/logout"
                 :pic (get-uid-field uid "picurl")
                 :add "New project"
                 :latest-projects (get-last-projects 10)}))
    (indextpl {:logged "Log in with github" :link "/github"
               :pic "" :add ""
               :latest-projects (get-last-projects 10)})))

(defn add [req]
  (if (authenticated? req)
    (let [authentications
          (get-in req [:session :cemerick.friend/identity :authentications])
          access-token (:access_token (second (first authentications)))
          basic (github-user-info access-token)
          username (:login basic)
          email (:email basic)
          uid (get-username-uid username)
          repos (filter-out-active-repos
                 (github-user-repos access-token) uid)]
      (addprojecttpl {:logged "Log out" :link "/logout"
                      :pic (get-uid-field uid "picurl")
                      :add "New project"
                      :repos repos
                      :uid uid}))
    (addprojecttpl {:logged "Log in with github" :link "/github"
                 :pic "" :add ""})))

(def gh-client-config
  {:client-id (System/getenv "github_client_id")
   :client-secret (System/getenv "github_client_secret")
   :callback {:domain (System/getenv "github_client_domain")
              :path "/github.callback"}})

(def friend-uri-config
  {:authentication-uri
   {:url "https://github.com/login/oauth/authorize"
    :query {:client_id (:client-id gh-client-config)
            :response_type "code"
            :redirect_uri
            (oauth2/format-config-uri gh-client-config)
            :scope "user"}}
   :access-token-uri
   {:url "https://github.com/login/oauth/access_token"
    :query {:client_id (:client-id gh-client-config)
            :client_secret (:client-secret gh-client-config)
            :grant_type "authorization_code"
            :redirect_uri
            (oauth2/format-config-uri gh-client-config)
            :code ""}}})

(def friend-config-auth {:roles #{:kickhub.core/user}})

;; OAuth2 config
(defn access-token-parsefn
  "Parse response to get an access-token."
  [response]
  (-> response
      :body
      codec/form-decode
      clojure.walk/keywordize-keys
      :access_token))

(defn wrap-friend [handler]
  "Wrap friend authentication around handler."
  (friend/authenticate
   handler
   {:allow-anon? true
    :default-landing-uri "/"
    :workflows
    [(oauth2/workflow
      {:client-config gh-client-config
       :uri-config friend-uri-config
       :login-uri "/github"
       :access-token-parsefn access-token-parsefn
       :config-auth friend-config-auth})]}))

(defn- github [req] "It works!")

(defn- logout [req]
  (friend/logout* (resp/redirect (str (:context req) "/"))))

;; FIXME
(defn- addprojectpage [{:keys [repo uid]}]
  (if (create-project repo uid)
    "Project created!"
    "Project exists already."))

;; FIXME
(defn- supportprojectpage [{:keys [amount pid uid]}]
  (pr-str (str amount " " pid " " uid)))

(defn- projectpage [pname]
  (let [pid (get-pname-pid pname)]
    (projecttpl
     (assoc (keywordize-array-mapize (get-pid-all pid))
       :pid pid))))

(defn- userpage [uname]
  (let [uid (get-username-uid uname)
        infos (keywordize-array-mapize (get-uid-all uid))]
    (usertpl (assoc infos :user-projects
                    (get-uid-projects uid)))))

(defroutes app-routes
  (GET "/" req (index req))
  (GET "/user/:uname/:pname" [uname pname] (projectpage pname))
  (GET "/user/:uname" [uname] (userpage uname))
  (GET "/github" req (github req))
  (GET "/logout" req (logout req))
  (GET "/add" req (add req))
  (POST "/addproject" {params :params} (addprojectpage params))
  (POST "/support" {params :params} (supportprojectpage params))
  (GET "/about" [] (abouttpl))
  (GET "/roadmap" [] (roadmaptpl))
  (route/resources "/")
  ;; FIXME: better 404 page
  (route/not-found (notfoundtpl)))

(def ring-handler
  (middleware/app-handler
   [(wrap-friend app-routes)]))
