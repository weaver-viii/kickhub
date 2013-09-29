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

(html/deftemplate index "kickhub/html/index.html"
  [ctxt]
  [:div#login-menu]
  (fn [match]
    (cond
     (and (authenticated? ctxt) (logged-in-normally? ctxt))
     ((html/content "Logged in normally") match)
     (authenticated? ctxt)
     ((html/content "Logged in with github") match)
     :else
     ((html/content "Please login with your account or github") match))))

(defn- login
  "Display the login page."
  [req]
  (h/html5
   (h/include-css "/css/kickhub.css")
   [:body
    [:h1 "KickHub"]
    [:h2 "Login"]
    [:form {:method "POST" :action "login"}
     [:div "Username: " [:input {:type "text" :name "username"}]]
     [:div "Password: " [:input {:type "password" :name "password"}]]
     [:div [:input {:type "submit" :class "button" :value "Login"}]]]]))

(defn- load-user
  "Load a user from her username."
  [username]
  (let [uid (get-username-uid username)
        password (get-uid-field uid "p")]
    {:username username :password (hash-bcrypt password) :roles #{::user}}))

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
    :default-landing-uri "/check"
    :workflows
    [(workflows/interactive-form
      :login-uri "/login"
      :credential-fn
      (partial creds/bcrypt-credential-fn load-user))
     (oauth2/workflow
      {:client-config gh-client-config
       :uri-config friend-uri-config
       :login-uri "/github"
       :access-token-parsefn access-token-parsefn
       :config-auth friend-config-auth})]}))

(defn- logout [req]
  (friend/logout* (resp/redirect (str (:context req) "/"))))

(defn- github [req]
  "It works!")

(defn- register
  "Register a new user account."
  ([]
     (h/html5
      (h/include-css "/css/kickhub.css")
      [:body
       [:h1 "KickHub"]
       [:form {:method "POST" :action "register"}
        [:div "Username: " [:input {:type "text" :name "username"}]]
        [:div "Email: " [:input {:type "text" :name "email"}]]
        [:div "Password: " [:input {:type "password" :name "password"}]]
        [:div [:input {:type "submit" :class "button" :value "Register"}]]]]))
  ([{:keys [username email password]}]
     (create-user username email password)))

(defroutes app-routes
  (GET "/" req (index req))
  (GET "/sendmail/:user" [user] (send-email user))
  (GET "/login" req (login req))
  (GET "/github" req (github req))
  (GET "/register" [] (register))
  (POST "/register" {params :params} (register params))
  (GET "/activate/:authid" [authid] (activate-user authid))
  (GET "/logout" req (logout req))
  (GET "/repos" req
       (friend/authorize #{:kickhub.core/user}
                         (render-repos-page req)))
  (GET "/emails" req
       (friend/authorize #{:kickhub.core/user}
                         (render-emails-page req)))
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
