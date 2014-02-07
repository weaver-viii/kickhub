(ns kickhub.html.templates
  (:require [cemerick.friend :as friend]
            [kickhub.model :refer :all]
            [kickhub.github :refer :all]
            [net.cgrand.enlive-html :as html]
            [net.cgrand.reload :as reload]
            [ring.util.response :as resp]))

;; FIXME: Need to double-check (again) if it works
(reload/auto-reload *ns*)

;;; * Utility functions

(defmacro maybe-substitute
  "Maybe substitute."
  ([expr] `(if-let [x# ~expr] (html/substitute x#) identity))
  ([expr & exprs] `(maybe-substitute (or ~expr ~@exprs))))

(defmacro maybe-content
  "Maybe content."
  ([expr] `(if-let [x# ~expr] (html/content x#) identity))
  ([expr & exprs] `(maybe-content (or ~expr ~@exprs))))

;;; * Templates

(html/deftemplate ^{:doc "Main index template"}
  index-tpl "kickhub/html/base.html"
  [{:keys [container logo-link msg menu gravatar]}]
  ;; In /about, the logo points to the index page
  [:#menu] (maybe-content menu)
  [:#gravatar :img] (html/set-attr :src (or gravatar ""))
  [:#msg :p] (maybe-content msg)
  [:#logo :a] (html/set-attr :href (or logo-link "/about"))
  ;; Set the menu item
  ;; [:#menu :ul :li :a.log1] ;; :> html/first-child]]]
  ;; (html/do->
  ;;  (html/content (or (last menu-link) "Login"))
  ;;  (html/set-attr :href (or (first menu-link) "/login")))
  ;; Set the content of the page
  [:#container] (maybe-content container))

;;; * Generic snippets

(html/defsnippet ^{:doc "Snippet for the TBA page."}
  tba "kickhub/html/messages.html" [:#tba] [])
(html/defsnippet ^{:doc "Snippet for the 404 page."}
  notfound "kickhub/html/messages.html" [:#notfound] [])
(html/defsnippet ^{:doc "Snippet for the /about page."}
  about "kickhub/html/messages.html" [:#about] [])
(html/defsnippet ^{:doc "Snippet for the /tos page."}
  tos "kickhub/html/messages.html" [:#tos] [])

;;; * Snippet for news

;; This snippet takes a string as arg and inserts it in the list item
;; within the .news html selector
(html/defsnippet ^{:doc "Snippet for a news."}
  anews "kickhub/html/news.html" [:.news]
  [an]
  [:li] (html/content an))

;; This snippet takes a list of news (strings) as arg and map over it
;; to insert news as list item within the #allnews html div selector
(html/defsnippet ^{:doc "Snippet for the news."}
  news "kickhub/html/news.html" [:#allnews]
  [ns]
  [:.newscontent] (html/content (map #(anews %) ns)))

(html/defsnippet ^{:doc "Snippet for the profile."}
  profile "kickhub/html/profile.html" [:#profile] [])

;;; * Projects and donations snippets

(def ^{:doc "Project selector." :dynamic true}
  *project-sel* [[:.project (html/nth-of-type 1)] :> html/first-child])

(html/defsnippet 
  ^{:doc "Project snippet." :dynamic true}
  my-project "kickhub/html/lists.html" *project-sel*
  [{:keys [name]}]
  [:a] (html/do->
        (html/content name)
        (html/set-attr :href (str (System/getenv "github_client_domain") "/project/" name))))

(html/defsnippet
  ^{:doc "List of Projects snippet." :dynamic true}
  my-projects "kickhub/html/lists.html" [:#my_projects]
  [projects]
  [:#content] (html/content (map #(my-project %) projects)))

(def ^{:doc "Donation selector" :dynamic true}
  *donation-sel*
  [[:.donation (html/nth-of-type 1)] :> html/first-child])

(html/defsnippet
  ^{:doc "Donation snippet." :dynamic true}
  my-donation "kickhub/html/lists.html" *donation-sel*
  [{:keys [amount]}]
  [:span] (html/content amount))

(html/defsnippet
  ^{:doc "List of donations snippet." :dynamic true}
  my-donations "kickhub/html/lists.html" [:#my_donations]
  [donations]
  [:#content1] (html/content (map #(my-donation %) donations)))

;; (render (html/emit* (my-projects (get-uid-projects (get-username-uid "bzg")))))
;; (render (html/emit* (map #(my-project %) (get-uid-projects (get-username-uid "bzg"))))))
;; (html/content (map #(my-project %) (get-uid-projects (get-username-uid "bzg"))))
;; (html/content (map #(my-donation %) (get-uid-transactions (get-username-uid "bzg"))))

;;; * Other snippets

(html/defsnippet ^{:doc "Snippet for the login form."}
  login "kickhub/html/forms.html" [:#login] [])
(html/defsnippet ^{:doc "Snippet for the register form."}
  register "kickhub/html/forms.html" [:#register] [username email]
  [:.input-group :#username] (html/set-attr :value username)
  [:.input-group :#email] (html/set-attr :value email))
(html/defsnippet ^{:doc "Snippet for the submit email form."}
  submit-email "kickhub/html/forms.html" [:#submit-email] [])
(html/defsnippet ^{:doc "Snippet for the submit project form."}
  submit-project "kickhub/html/forms.html" [:#submit-project] [])
(html/defsnippet ^{:doc "Snippet for the submit donation form."}
  submit-donation "kickhub/html/forms.html" [:#submit-donation] [])
(html/defsnippet ^{:doc "Snippet for the submit profile form."}
  submit-profile "kickhub/html/forms.html" [:#submit-profile] [])
(html/defsnippet ^{:doc "Snippet for the logged menu."}
  logged-menu "kickhub/html/forms.html"
  [:#logged-menu]
  [username]
  [:ul :li :a.profile]
  (html/set-attr :href (str (System/getenv "github_client_domain")
                            "/user/" username)))

(html/defsnippet ^{:doc "Snippet for the unlogged menu."}
  unlogged-menu "kickhub/html/forms.html" [:#unlogged-menu] [])

;;; * Views

(defn index-tba-page
  "Generate the index TBA page."
  [params]
  (index-tpl {:container (concat (tba) (submit-email))
              :menu (unlogged-menu)
              :msg (if (:m params)
                     (str (:m params) " is now subscribed")
                     "")}))

(defn index-page
  "Generate the index page."
  [req & {:keys [msg]}]
  (let [id (friend/identity req)]
    (index-tpl {:container
                (news (map news-to-sentence (reverse (get-news))))
                :menu (if id (logged-menu (:current id)) (unlogged-menu))
                :gravatar (get-uid-field (get-username-uid (:current id)) "picurl")
                :msg (or msg (if id
                               "You are now logged in"
                               "Please login or register"))})))

(defn login-page
  "Generate the login page."
  [req]
  (let [params (clojure.walk/keywordize-keys (:form-params req))
        id (friend/identity req)]
    (index-tpl {:container (if id "You are already logged in" (login))
                :menu (unlogged-menu)
                :gravatar (get-uid-field (get-username-uid (:current id)) "picurl")
                :msg (if (= (:login_failed params) "Y")
                       "Error when logging in..."
                       "")})))

(defn register-page
  "Generate the register page."
  [params]
  (index-tpl {:container (register "Username" "Email")
              :menu (unlogged-menu)}))

(defn register-page-gh
  "Register via Github."
  [request]
  (let [authentications
        (get-in request [:session :cemerick.friend/identity :authentications])
        access-token (:identity (second (first authentications)))
        user_infos (github-user-info access-token)
        user_basic_infos (github-user-basic-info access-token)]
    ;; FIXME
    ;; (if (get-username-uid (:username user_basic_infos))
    ;;   (index-page request)
    (index-tpl {:msg ;;(:login user_basic_infos)
                "Select a password for your new account"
                :container (register (:username user_basic_infos)
                                     (:email user_basic_infos))
                :gravatar (:picurl user_basic_infos)
                :menu (logged-menu (:username user_basic_infos))})))
  
(defn submit-project-page
  "Generate the page to submit a project."
  [req]
  (let [route-params (clojure.walk/keywordize-keys (:route-params req))
        id (friend/identity req)
        params (clojure.walk/keywordize-keys (:form-params req))
        name (:name params)
        repo (:repo params)
        username (:current id)
        uid (get-username-uid username)]
  (if (not (empty? params))
    (do (create-project name repo uid)
        (resp/redirect (str "/user/" username)))
    (index-tpl {:container (submit-project)
                :gravatar (get-uid-field uid "picurl")
                :menu (logged-menu (:current id))}))))

(defn submit-donation-page
  "Generate the page to submit a donation."
  [req]
  (let [route-params (clojure.walk/keywordize-keys (:route-params req))
        id (friend/identity req)
        params (clojure.walk/keywordize-keys (:form-params req))
        amount (:amount params)
        pid (:pid params)
        username (:current id)
        fuid (get-username-uid username)
        uid (get-pid-field pid "by")]
    (if (not (empty? params))
      (do (create-transaction amount pid uid fuid)
          (resp/redirect (str "/user/" username)))
      (index-tpl {:container (submit-donation)
                  :gravatar (get-uid-field fuid "picurl")
                  :menu (logged-menu (:current id))}))))

(defn user-page
  "Generate the page to display a user information."
  [req]
  (let [;; params (clojure.walk/keywordize-keys (:form-params req))
        route-params (clojure.walk/keywordize-keys (:route-params req))
        username (:username route-params)
        uid (get-username-uid username)
        id (friend/identity req)]
    (index-tpl {:container
                (concat (my-projects (get-uid-projects uid))
                        (my-donations (get-uid-transactions uid)))
                :gravatar (get-uid-field uid "picurl")
                :menu (if id (logged-menu (:current id)) (unlogged-menu))})))

(defn project-page
  "Generate the page to display a project information."
  [req]
  (let [;; params (clojure.walk/keywordize-keys (:form-params req))
        route-params (clojure.walk/keywordize-keys (:route-params req))
        pname (:pname route-params)
        pid (get-pname-pid pname)
        id (friend/identity req)]
    (index-tpl {:container (str "Project: " (get-pid-field pid "name")
                                " by " (get-uid-field
                                        (get-pid-field pid "by") "u"))
                :menu (if id (logged-menu (:current id)) (unlogged-menu))})))

;; FIXME
;; (defmacro with-id [req body]
;;   (let [route-params (clojure.walk/keywordize-keys (:route-params req))
;;         id (friend/identity req)]
;;     ~@body))

;; (macroexpand with-id)

(defn notfound-page "Generate the 404 page."
  []
  (index-tpl {:container (notfound)
              :menu ""}))

(defn about-page "Generate the about page."
  [req]
  (let [route-params (clojure.walk/keywordize-keys (:route-params req))
        id (friend/identity req)]
    (index-tpl {:container (about) :logo-link "/"
                :gravatar (get-uid-field (get-username-uid (:current id)) "picurl")
                :menu (if id (logged-menu (:current id)) (unlogged-menu))})))

(defn tos-page "Generate the tos page."
  [req]
  (let [route-params (clojure.walk/keywordize-keys (:route-params req))
        id (friend/identity req)]
    (index-tpl {:container (tos)
                :gravatar (get-uid-field (get-username-uid (:current id)) "picurl")
                :menu (if id (logged-menu (:current id)) (unlogged-menu))})))

;;; * Local variables

;; Local Variables:
;; eval: (orgstruct-mode 1)
;; orgstruct-heading-prefix-regexp: ";;; "
;; End:
