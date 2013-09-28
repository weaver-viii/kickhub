(ns kickhub.core
  (:require
   [noir.util.middleware :as middleware]
   [ring.util.response :as resp]
   [compojure.core :as compojure :refer (GET POST defroutes)]
   (compojure [handler :as handler]
              [route :as route])
   [hiccup.page :as h]
   [postal.core :as postal]
   [cemerick.friend :as friend]
   (cemerick.friend [workflows :as workflows]
                    [credentials :as creds])
   [cemerick.friend.credentials :refer (hash-bcrypt)]))

(defn- index []
  (h/html5
   (h/include-css "/css/kickhub.css")
   [:body "Hello!"]))

(defn- send-email [user]
  (postal/send-message
   ^{;; :user login
     ;; :pass password
     :host "localhost"
     :port 25}
   {:from "Bastien <bzg@bzg.fr>"
    :to user
    :subject "Thanks for testing KickHub!"
    :body "Hopefully this message will be more interesting soon."})
  "Email sent.")

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

(defn- load-users [username]
  {:username username
   :password (hash-bcrypt "password")
   :roles #{::user}})

(defn wrap-friend [handler]
  "Wrap friend authentication around handler."
  (friend/authenticate
   handler
   {:allow-anon? true
    :workflows [(workflows/interactive-form
                 ;; :allow-anon? true
                 ;; :login-uri "/login"
                 ;; :default-landing-uri "/login"
                 :credential-fn
                 (partial creds/bcrypt-credential-fn load-users))]}))

(defn- logout [req]
  (friend/logout* (resp/redirect (str (:context req) "/"))))

(defroutes app-routes
  (GET "/" [] (index))
  (GET "/sendmail/:user" [user] (send-email user))
  (GET "/login" req (login req))
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
