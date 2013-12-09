(ns kickhub.core
  (:require [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [clojurewerkz.scrypt.core :as sc]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :as route]
            [friend-oauth2.workflow :as oauth2]
            [friend-oauth2.util :as oauth2-util]
            [kickhub.html.templates :refer :all]
            [kickhub.model :refer :all]
            [kickhub.github :refer :all]
            [kickhub.rss :refer :all]
            [noir.util.middleware :as middleware]
            [ring.util.response :as resp]
            [ring.util.codec :as codec]))

;;; * Friend interactive form authentication

;; Variables and functions in this section are used to let the user
;; authenticate through a traditional interactive form.  Password are
;; encrypted using scrypt (https://en.wikipedia.org/wiki/Scrypt)
;; before they are stored in the redis database.

(def ^{:doc "Default roles for Friend authenticated users."
       :private true}
  friend-config-auth
  {:roles #{:kickhub.core/users}})

(defn- logout
  "Log out authenticated users."
  [req]
  (friend/logout* (resp/redirect (str (:context req) "/"))))

(defn- load-user
  "Load a user from her username."
  [username]
  (let [uid (get-username-uid username)
        password (get-uid-field uid "p")]
    {:username username :password password :roles #{::users}}))

(defn- scrypt-credential-fn
  "Variant of `bcrypt-credential-fn` using scrypt."
  [load-credentials-fn {:keys [username password]}]
  (when-let [creds (load-credentials-fn username)]
    (let [password-key (or (-> creds meta ::password-key) :password)]
      (when (sc/verify password (get creds password-key))
        (dissoc creds password-key)))))

(defn- wrap-friend
  "Wrap friend interactive form authentication around `handler`."
  [handler]
  (friend/authenticate
   handler
   {:allow-anon? true
    :workflows [(workflows/interactive-form
                 :allow-anon? true
                 :login-uri "/login"
                 ;; :redirect-on-auth? "/" ;; This is the default
                 :credential-fn (partial scrypt-credential-fn load-user)
                 :config-auth friend-config-auth)]}))

;;; * Friend Github authentication

;; Variables and functions in this section are used for GitHub
;; authentication.  Three environment variables are requested:
;; - github_client_id
;; - github_client_secret
;; - github_client_domain

(def ^{:doc "Get the GitHub app configuration from environment variables."
       :private true}
  gh-client-config
  {:client-id (System/getenv "github_client_id")
   :client-secret (System/getenv "github_client_secret")
   :callback {:domain (System/getenv "github_client_domain")
              :path "/github.callback"}})

(def ^{:doc "Set up the GitHub authentication URI for Friend."
       :private true}
  friend-gh-uri-config
  {:authentication-uri
   {:url "https://github.com/login/oauth/authorize"
    :query {:client_id (:client-id gh-client-config)
            :response_type "code"
            :redirect_uri
            (oauth2-util/format-config-uri gh-client-config)
            :scope "user:email"}}
   :access-token-uri
   {:url "https://github.com/login/oauth/access_token"
    :query {:client_id (:client-id gh-client-config)
            :client_secret (:client-secret gh-client-config)
            :grant_type "authorization_code"
            :redirect_uri
            (oauth2-util/format-config-uri gh-client-config)
            :code ""}}})

(defn- access-token-parsefn
  "Parse the response to get an access-token."
  [response]
  (-> response
      :body
      codec/form-decode
      clojure.walk/keywordize-keys
      :access_token))

(defn- wrap-friend-github
  "Wrap friend authentication around `handler`."
  [handler]
  (friend/authenticate
   handler
   {:allow-anon? true
    :default-landing-uri "/"
    :workflows
    [(oauth2/workflow
      {:client-config gh-client-config
       :uri-config friend-gh-uri-config
       :login-uri "/github"
       :access-token-parsefn access-token-parsefn
       :config-auth friend-config-auth})]}))

;;; * Routes

;; This section contains the main routes definition along with the
;; main handler, as called in the `immutant.init` namespace.

(defroutes ^{:doc "Main application routes."
             :private true}
  app-routes
  ;; (GET "/" {params :params} (index-tba-page params))
  ;; (POST "/" {params :params} (email-to-mailing params))
  (GET "/" req (index-page req))
  (GET "/login" req (login-page req))
  (GET "/activate/:authid" [authid]
       (friend/authorize
        #{::users}
        (do (activate-user authid)
            (index-page nil :msg "User activated, please log in"))))
  (GET "/confirm/:authid" [authid]
       (friend/authorize
        #{::users}
        (do (confirm-transaction authid)
            (index-page nil :msg "Transaction confirmed, thanks"))))

  (GET "/user/:username" req (user-page req))
  (GET "/project/:pname" req (project-page req))

  (GET "/rss" [] (rss))
  (GET "/register" [] (register-page nil))
  (POST "/register" {params :params} (register-user params))

  (GET "/newproject" req (submit-project-page req))
  (POST "/newproject" req (submit-project-page req))
  (GET "/donation" req (submit-donation-page req))
  (POST "/donation" req (submit-donation-page req))

  (GET "/about" req (about-page req))
  (GET "/tos" req (tos-page req))
  (GET "/logout" req (logout req))
  (GET "/test" req (if-let [identity (friend/identity req)] (pr-str identity) "notloggedin"))
  (route/resources "/")
  (route/not-found (notfound-page)))

(def ^{:doc "Main ring handler."}
  ring-handler
  (middleware/app-handler
   [(wrap-friend-github (wrap-friend app-routes))]))

;;; * Local variables

;; Local Variables:
;; eval: (orgstruct-mode 1)
;; orgstruct-heading-prefix-regexp: ";;; "
;; End:

