(ns kickhub.core
  (:require [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [clojurewerkz.scrypt.core :as sc]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :as route]
            [kickhub.html.templates :refer :all]
            [kickhub.model :refer :all]
            [noir.util.middleware :as middleware]
            [ring.util.response :as resp]))

;;; * Friend

(defn- logout [req]
  (friend/logout* (resp/redirect (str (:context req) "/"))))

(defn- load-user
  "Load a user from her username."
  [username]
  (let [uid (get-username-uid username)
        password (get-uid-field uid "p")]
    {:username username :password password :roles #{::users}}))

;; Variant of bcrypt-credential-fn using scrypt
(defn- scrypt-credential-fn
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
                 ;; :redirect-on-auth? "/" ;; This is the default
                 :credential-fn
                 (partial scrypt-credential-fn load-user))]}))

;;; * Routes

(defroutes app-routes
  ;; (GET "/" {params :params} (index-tba-page params))
  ;; (POST "/" {params :params} (email-to-mailing params))
  
  (GET "/" req (index-page req))
  (GET "/login" req (login-page req))
  (GET "/activate/:authid" [authid]
       (friend/authorize
        #{::users}
        (do (activate-user authid)
            (index-page nil {:msg "User activated, please log in"
                             :nomenu ""}))))
  (GET "/confirm/:authid" [authid]
       (friend/authorize
        #{::users}
        (do (confirm-transaction authid)
            (index-page nil {:msg "Transaction confirmed, thanks"}))))

  (GET "/user/:username" req (user-page req))
  (GET "/project/:pname" req (project-page req))

  (GET "/register" [] (register-page nil))
  (POST "/register" {params :params} (register-user params))

  (GET "/newproject" [] (submit-project-page nil nil))
  (POST "/newproject" req (submit-project-page req (friend/identity req)))
  (GET "/donation" [] (submit-donation-page nil nil))
  (POST "/donation" req (submit-donation-page req (friend/identity req)))

  (GET "/about" [] (about-page))
  (GET "/tos" [] (tos-page))
  (GET "/logout" req (logout req))
  (GET "/test" req (if-let [identity (friend/identity req)] (pr-str identity) "notloggedin"))
  (route/resources "/")
  (route/not-found (notfound-page)))

(def ring-handler
  (middleware/app-handler
   [(wrap-friend app-routes)]))

;;; * Old github auth code

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

;; Local Variables:
;; eval: (orgstruct-mode 1)
;; orgstruct-heading-prefix-regexp: ";;; "
;; End:

