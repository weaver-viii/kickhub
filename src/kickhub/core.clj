(ns kickhub.core
  (:require
   [noir.util.middleware :as middleware]
   [ring.util.response :as resp]
   [ring.util.codec :as codec]
   [compojure.core :as compojure :refer (GET POST defroutes)]
   (compojure [handler :as handler]
              [route :as route])
   [hiccup.page :as h]
   [hiccup.element :as e]
   [digest :as digest]
   [postal.core :as postal]
   [cemerick.friend :as friend]
   (cemerick.friend [workflows :as workflows]
                    [credentials :as creds])
   [cemerick.friend.credentials :refer (hash-bcrypt)]
   [friend-oauth2.workflow :as oauth2]
   [taoensso.carmine :as car]))

(def server1-conn
  {:pool {} :spec {:uri "redis://localhost:6379/"}})

(defmacro wcar* [& body]
  `(car/wcar server1-conn ~@body))

(defn- index [req]
  (let [identity (friend/identity req)
        msg (if identity
              (apply str "You are logged in, with these roles: "
                     (-> identity friend/current-authentication :roles)
                     "<br/>"
                     "<a href=\"/github\">Associate</a> your github account")
              "Please <a href=\"/login\">log in</a>")]
    (h/html5
     (h/include-css "/css/kickhub.css")
     [:body "Hello!"
      [:p "Login with"]])))

(defn- send-email [email activation-link]
  (postal/send-message
   ^{:host "localhost"
     :port 25}
   {:from "Bastien <bzg@bzg.fr>"
    :to email
    :subject "Thanks for testing KickHub!"
    :body (str "Please click on the link below to activate your account:\n"
               activation-link)})
  "Email sent!")

(defn- login
  "Display the login page."
  [req]
  (h/html5
   (h/include-css "/css/kickhub.css")
   [:body
    [:h1 "KickHub"]
    [:h2 "Login"]
    [:form {:method "POST" :action "login"}
     [:div "Email: " [:input {:type "text" :name "username"}]]
     [:div "Password: " [:input {:type "password" :name "password"}]]
     [:div [:input {:type "submit" :class "button" :value "Login"}]]]]))

(defn- activate-user [authid]
  (let [guid (wcar* (car/get (str "auth:" authid)))]
    (wcar* (car/hset (str "uid:" guid) "active" 1)))
  (h/html5
   [:body
    [:h1 "You are now an active user."]]))

(defn- load-users [username]
  {:username username
   :password (hash-bcrypt "password")
   :roles #{::user}})

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
    [
     (workflows/interactive-form
      :login-uri "/login"
      :credential-fn
      (partial creds/bcrypt-credential-fn load-users))
     (oauth2/workflow
      {:client-config gh-client-config
       :uri-config friend-uri-config
       :login-uri "/github"
       :access-token-parsefn access-token-parsefn
       :config-auth friend-config-auth})
     ]}))

(defn- logout [req]
  (friend/logout* (resp/redirect (str (:context req) "/"))))

(defn- github [req]
  "Ça marche!")

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
     (let [guid (wcar* (car/incr "global:uid"))
           authid (digest/md5 (str (System/currentTimeMillis) username))]
       (wcar* (car/hmset
               (str "uid:" guid)
               "u" username "p" password "e" email
               "pic" (format "<img src=\"http://www.gravatar.com/avatar/%s\" />"
                             (digest/md5 email))
               "updated" (java.util.Date.)
               "active" 0)
              (car/set (str "uid:" guid ":auth") authid)
              (car/set (str "auth:" authid) guid)
              (car/set (str "user:" username ":uid") guid)
              (car/rpush "users" guid))
       (send-email email (str (System/getenv "github_client_domain") "/activate/" authid))
       (str "Registered!  Thanks.<br/>"
            (format "<img src=\"http://www.gravatar.com/avatar/%s\" />"
                    (digest/md5 email))))))

(defroutes app-routes
  (GET "/" req (index req))
  (GET "/sendmail/:user" [user] (send-email user))
  (GET "/login" req (login req))
  (GET "/github" req (github req))
  (GET "/register" [] (register))
  (POST "/register" {params :params} (register params))
  (GET "/activate/:authid" [authid] (activate-user authid))
  (GET "/logout" req (logout req))
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
