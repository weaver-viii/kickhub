(ns kickhub.handler
  (:require
   [noir.util.middleware :as middleware]
   [noir.session :as session]
   [taoensso.carmine :as car]
   [clojurewerkz.scrypt.core :as sc]
   [ring.middleware.reload :refer :all]
   [compojure.core :as compojure :refer (GET POST defroutes)]
   [org.httpkit.server :refer :all]
   (compojure [route :as route])
   ;; [clojure.string :as s]
   [ring.util.response :as resp]
   ;; [ring.util.codec :as codec]
   ;; [friend-oauth2.workflow :as oauth2]
   ;; [friend-oauth2.util :as oauth2-util]
   [cemerick.friend :as friend]
   (cemerick.friend [workflows :as workflows]
                    [credentials :as creds])
   [kickhub.model :refer :all]
   [kickhub.views.templates :refer :all]))

(derive ::admins ::users)

(defn- load-user
  "Load a user from her username."
  [username]
  (let [admin (System/getenv "kickhub_admin")
        uid (get-username-uid username)
        password (get-uid-field uid "password")]
    ;; FIXME This is adhoc and temporary
    (if (= username admin) (session/put! :admin "yes"))
    (session/put! :username username)
    {:identity username :password password
     :roles (if (= username admin) #{::admins} #{::users})}))

(defn- scrypt-credential-fn
  "Variant of `bcrypt-credential-fn` using scrypt."
  [load-credentials-fn {:keys [username password]}]
  (when-let [creds (load-credentials-fn username)]
    (let [password-key (or (-> creds meta ::password-key) :password)]
      (when (sc/verify password (get creds password-key))
        (dissoc creds password-key)))))

(defn- wrap-friend [handler]
  "Wrap friend authentication around handler."
  (friend/authenticate
   handler
   {:allow-anon? true
    :workflows [(workflows/interactive-form
                 :allow-anon? true
                 :login-uri "/login"
                 :default-landing-uri "/"
                 :credential-fn (partial scrypt-credential-fn load-user))
                ]}))

(defroutes app-routes
  (GET "/" req
       (main-tpl {:title "kickhub.com - donate to free culture"
                  :content (tasks-list)}))
  (GET "/login" req
       (main-tpl {:title "kickhub.com - login"
                  :content (login)}))
  (GET "/task/:tskid" [tskid]
       (main-tpl {:title "kickhub.com - task"
                  :content (show-task (get-tskid-all tskid))}))
  (GET "/task_new" req
       (friend/authorize #{::users}
                         (main-tpl {:title "kickhub.com - add a new task"
                                    :content (new-task)})))
  (POST "/task_new" {params :params}
        (do (create-task params)
            (main-tpl {:title "kickhub.com - thanks!"
                       :content "Your new task has been added."})))
  (POST "/transaction_process" {params :params}
        (process-transaction params))
  (GET "/register" []
       (main-tpl {:title "kickhub.com - register"
                  :content (register)}))
  (POST "/register" {params :params}
        (do (create-user (:username params)
                         (:email params)
                         (:password params))
            (main-tpl {:content "User registered, please log in."})))
  (GET "/activate/:authid" [authid]
       (friend/authorize
        #{::users}
        (do (let [guid (wcar* (car/get (str "auth:" authid)))]
              (wcar* (car/hset (str "uid:" guid) "active" 1)))
            (main-tpl {:content "User activated, please log in."}))))
  (GET "/logout" req
       (friend/logout* (resp/redirect (str (:context req) "/"))))
  (route/resources "/")
  (route/not-found "Not found"))

(def app (wrap-reload
          (middleware/app-handler
           [(wrap-friend app-routes)])))

(defn -main [& args]
  (run-server #'app {:port 8080}))
