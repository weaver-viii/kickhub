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

;; Shamelessly taken from Swannodette enlive tutorial
(def ^:dynamic *link-sel*
  [[:.content (html/nth-of-type 1)] :> html/first-child])

(html/defsnippet link-model "kickhub/html/index.html" *link-sel*
  [{:keys [text href]}]
  [:a] (html/do->
        (html/content text)
        (html/set-attr :href href)))

(html/deftemplate indextpl "kickhub/html/index.html"
  [{:keys [logged link pic add latest-projects]}]
  [:#login :a.logged] (html/do-> (html/content logged) (html/set-attr :href link))
  [:#login :a.addproject] (html/content add)
  [:#login :img.pic] (html/set-attr :src pic)
  [:#new-projects]
  (html/content
   (map #(link-model {:text (:name %) :href (:name %)})
        latest-projects)))

(html/defsnippet option-model "kickhub/html/addproject.html" [:option]
  [{:keys [text value]}]
  [:option] (html/do->
             (html/content text)
             (html/set-attr :value value)))

(html/deftemplate addproject "kickhub/html/addproject.html"
  [{:keys [logged link pic add repos uid]}]
  [:#login :a.logged] (html/do-> (html/content logged) (html/set-attr :href link))
  [:#login :a.addproject] (html/content add)
  [:#login :img.pic] (html/set-attr :src pic)
  [:#new-projects :form :#huid] (html/set-attr :value uid)
  [:#new-projects :form :select]
  (html/content
   (map #(option-model {:text (:name %) :value (:name %)}) repos)))

(html/deftemplate about "kickhub/html/about.html" [])
(html/deftemplate notfound "kickhub/html/notfound.html" [])

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
                 :add "Add new project"
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
          repos (github-user-repos access-token)]
      (addproject {:logged "Log out" :link "/logout"
                   :pic (get-uid-field uid "picurl")
                   :add "Add new project"
                   :repos repos
                   :uid uid}))
    (addproject {:logged "Log in with github" :link "/github"
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
(defn- newproject [{:keys [repo uid]}]
  (create-project repo uid)
  "Project created!")

(defroutes app-routes
  (GET "/" req (index req))
  (GET "/github" req (github req))
  (GET "/logout" req (logout req))
  ;; FIXME: temporary test
  (GET "/projects" [] (pr-str (get-last-projects 10)))
  (GET "/add" req (add req))
  (GET "/about" req (about))
  (POST "/addproject" {params :params} (newproject params))
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
  ;; FIXME: better 404 page
  (route/not-found (notfound)))

(def ring-handler
  (middleware/app-handler
   [(wrap-friend app-routes)]))
