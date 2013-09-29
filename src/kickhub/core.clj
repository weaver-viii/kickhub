(ns kickhub.core
  (:require
   [noir.util.middleware :as middleware]
   [ring.util.response :as resp]
   [kickhub.model :refer :all]
   [kickhub.github :refer :all]
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
   [friend-oauth2.workflow :as oauth2]
   [net.cgrand.enlive-html :as html]))

(defn- authenticated? [req]
  (get-in req [:session :cemerick.friend/identity]))

(html/deftemplate indextpl "kickhub/html/index.html"
  [{:keys [logged pic]}]
  [:div#login :a.logged] (html/content logged)
  [:div#login :img.pic] (html/set-attr :src pic))

(defn index [req]
  (if (authenticated? req)
    (let [authentications
          (get-in req [:session :cemerick.friend/identity :authentications])
          access-token (:access_token (second (first authentications)))
          basic (github-user-info access-token)]
      (or (not (empty? (get-username-uid (:login basic))))
          (create-user (:login basic) (:email basic)))
      (indextpl {:logged "Logged in"
                 :pic (get-uid-field (get-username-uid (:login basic)) "picurl")}))
    (indextpl {:logged "Log in with github" :pic ""})))

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

(defroutes app-routes
  (GET "/" req (index req))
  (GET "/github" req (github req))
  (GET "/logout" req (logout req))
  (GET "/repos" req
       (friend/authorize #{:kickhub.core/user}
                         (render-repos-page req)))
  (GET "/user" req
       (friend/authorize #{:kickhub.core/user}
                         (render-user-page req)))
  (GET "/check" req
       (if-let [identity (friend/identity req)]
         (apply str "Logged in, with these roles: "
                (-> identity friend/current-authentication :roles))
         "You are an anonymous user."))
  (route/resources "/")
  (route/not-found "Sorry, page not found."))

(def ring-handler
  (middleware/app-handler
   [(wrap-friend app-routes)]))
