(ns kickhub.core
  (:require
   [noir.util.middleware :as middleware]
   [ring.util.response :as resp]
   [kickhub.model :refer :all]
   ;; [kickhub.github :refer :all]
   [kickhub.html.templates :refer :all]
   [ring.util.codec :as codec]
   [compojure.core :as compojure :refer (GET POST defroutes)]
   (compojure [handler :as handler]
              [route :as route])
   [cemerick.friend :as friend]
   (cemerick.friend [workflows :as workflows]
                    [credentials :as creds])
   [cemerick.friend.credentials :refer (hash-bcrypt)]
   [friend-oauth2.workflow :as oauth2]))

;; (defn- authenticated? [req]
;;   (get-in req [:session :cemerick.friend/identity]))

;; (def gh-client-config
;;   {:client-id (System/getenv "github_client_id")
;;    :client-secret (System/getenv "github_client_secret")
;;    :callback {:domain (System/getenv "github_client_domain")
;;               :path "/github.callback"}})

;; (def friend-uri-config
;;   {:authentication-uri
;;    {:url "https://github.com/login/oauth/authorize"
;;     :query {:client_id (:client-id gh-client-config)
;;             :response_type "code"
;;             :redirect_uri
;;             (oauth2/format-config-uri gh-client-config)
;;             :scope "user"}}
;;    :access-token-uri
;;    {:url "https://github.com/login/oauth/access_token"
;;     :query {:client_id (:client-id gh-client-config)
;;             :client_secret (:client-secret gh-client-config)
;;             :grant_type "authorization_code"
;;             :redirect_uri
;;             (oauth2/format-config-uri gh-client-config)
;;             :code ""}}})

;; (def friend-config-auth {:roles #{:kickhub.core/user}})

;; OAuth2 config
;; (defn access-token-parsefn
;;   "Parse response to get an access-token."
;;   [response]
;;   (-> response
;;       :body
;;       codec/form-decode
;;       clojure.walk/keywordize-keys
;;       :access_token))

;; (defn wrap-friend [handler]
;;   "Wrap friend authentication around handler."
;;   (friend/authenticate
;;    handler
;;    {:allow-anon? true
;;     :default-landing-uri "/"
;;     :workflows
;;     [(oauth2/workflow
;;       {:client-config gh-client-config
;;        :uri-config friend-uri-config
;;        :login-uri "/github"
;;        :access-token-parsefn access-token-parsefn
;;        :config-auth friend-config-auth})]}))

;; (defn- addprojectpage [req]
;;   (if (authenticated? req)
;;     (let [params (clojure.walk/keywordize-keys (:form-params req))
;;           repo (:repo params)
;;           fuid (:uid params)]
;;       (if (create-project repo fuid)
;;         (resp/redirect (str (:context req) "/"))))
;;     (resp/redirect (str (:context req) "/"))))
        
;; (defn- supportprojectpage [req]
;;   (if (authenticated? req)
;;     (let [params (clojure.walk/keywordize-keys (:form-params req))
;;           amount (:amount params)
;;           pid (:pid params)
;;           uid (:uid params)
;;           authentications
;;           (get-in req [:session :cemerick.friend/identity :authentications])
;;           access-token (:access_token (second (first authentications)))
;;           basic (github-user-info access-token)
;;           fuid (:login basic)]
;;       (create-transaction amount pid uid fuid)
;;       (resp/redirect (str (:context req) "/")))
;;     (resp/redirect (str (:context req) "/"))))

;; (defn- projectpage [pname]
;;   (let [pid (get-pname-pid pname)]
;;     (projecttpl
;;      (assoc (keywordize-array-mapize (get-pid-all pid))
;;        :pid pid))))

;; (defn- userpage [uname]
;;   (let [uid (get-username-uid uname)
;;         infos (keywordize-array-mapize (get-uid-all uid))]
;;     (usertpl (assoc infos :user-projects
;;                     (get-uid-projects uid)))))

(defn- logout [req]
  (friend/logout* (resp/redirect (str (:context req) "/"))))

(defn- load-user
  "Load a user from her username."
  [username]
  (let [uid (get-username-uid username)
        password (get-uid-field uid "p")]
    {:username username :password (hash-bcrypt password) :roles #{::users}}))

(defn wrap-friend [handler]
  "Wrap friend authentication around handler."
  (friend/authenticate
   handler
   {:allow-anon? true
    :workflows [(workflows/interactive-form
                 :allow-anon? true
                 :login-uri "/login"
                 :default-landing-uri "/"
                 :credential-fn
                 (partial creds/bcrypt-credential-fn load-user))]}))

(defroutes app-routes
  ;; (GET "/" {params :params} (index-tba-page params))
  ;; (POST "/" {params :params} (email-to-mailing params))
  
  (GET "/" req (index-page (friend/identity req)))
  (GET "/login" {params :params} (login-page params))
  (GET "/activate/:authid" [authid]
       (do (activate-user authid)
           (index-page {:msg "User activated, please log in"})))
  (GET "/user" req (if (friend/identity req)
                     (user-page req (friend/identity req))
                     (resp/redirect "/login")))
  (GET "/profile" [] (profile-page))

  (GET "/register" [] (register-page nil))
  (POST "/register" {params :params} (register-user params))

  (GET "/project" [] (submit-project-page nil nil))
  (POST "/project" req (submit-project-page req (friend/identity req)))
  (GET "/donation" [] (submit-donation-page nil nil))
  (POST "/donation" req (submit-donation-page req (friend/identity req)))

  (GET "/about" [] (about-page))
  (GET "/tos" [] (tos-page))
  (GET "/logout" req (logout req))
  (GET "/test" req (if-let [identity (friend/identity req)] (pr-str identity) "notloggedin"))
  (route/resources "/")
  (route/not-found (notfound-page))
  ;; (GET "/user/:uname/:pname" [uname pname] (projectpage pname))
  ;; (GET "/user/:uname" [uname] (userpage uname))
  ;; (GET "/add" req (add req))
  ;; (POST "/addproject" req (addprojectpage req))
  ;; (POST "/support" req (supportprojectpage req))
  )

(def ring-handler
  (middleware/app-handler
   [(wrap-friend app-routes)]))
