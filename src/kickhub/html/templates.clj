(ns kickhub.html.templates
  (:require [cemerick.friend :as friend]
            [kickhub.model :refer :all]
            [net.cgrand.enlive-html :as html]
            [net.cgrand.reload :as reload]
            [ring.util.response :as resp]))

(reload/auto-reload *ns*)

;;; * Utility functions

(defmacro maybe-substitute
  ([expr] `(if-let [x# ~expr] (html/substitute x#) identity))
  ([expr & exprs] `(maybe-substitute (or ~expr ~@exprs))))

(defmacro maybe-content
  ([expr] `(if-let [x# ~expr] (html/content x#) identity))
  ([expr & exprs] `(maybe-content (or ~expr ~@exprs))))

;;; * Templates

(html/deftemplate ^{:doc "Main index template"}
  index-tpl "kickhub/html/base.html"
  [{:keys [container logo-link msg nomenu]}]
  ;; In /about, the logo points to the index page
  [:#menu] (maybe-substitute nomenu)
  [:#msg :p] (maybe-content msg)
  [:#logo :a] (html/set-attr :href (or logo-link "/about"))
  ;; Set the menu item
  ;; [:#menu :ul :li :a.log1] ;; :> html/first-child]]]
  ;; (html/do->
  ;;  (html/content (or (last menu-link) "Login"))
  ;;  (html/set-attr :href (or (first menu-link) "/login")))
  ;; Set the content of the page
  [:#container] (maybe-content container))

;;; * Snippets

(html/defsnippet ^{:doc "Snippet for the TBA page."}
  tba "kickhub/html/messages.html" [:#tba] [])
(html/defsnippet ^{:doc "Snippet for the 404 page."}
  notfound "kickhub/html/messages.html" [:#notfound] [])
(html/defsnippet ^{:doc "Snippet for the /about page."}
  about "kickhub/html/messages.html" [:#about] [])
(html/defsnippet ^{:doc "Snippet for the /tos page."}
  tos "kickhub/html/messages.html" [:#tos] [])

(html/defsnippet ^{:doc "Snippet for the news."}
  news "kickhub/html/news.html" [:#allnews] [])
(html/defsnippet ^{:doc "Snippet for the profile."}
  profile "kickhub/html/profile.html" [:#profile] [])

;;; * Projects and donations snippets

(def ^{:doc "Enlive project selector" :dynamic true}
  *project-sel* [[:.project (html/nth-of-type 1)] :> html/first-child])

(html/defsnippet my-project "kickhub/html/lists.html" *project-sel*
  [{:keys [name]}]
  [:a] (html/do->
        (html/content name)
        (html/set-attr :href (str "http://localhost:8080/project/" name))))

(html/defsnippet my-projects "kickhub/html/lists.html" [:#my_projects]
  [projects]
  [:#content] (html/content (map #(my-project %) projects)))

(def ^{:doc "Enlive donation selector" :dynamic true}
  *donation-sel* [[:.donation (html/nth-of-type 1)] :> html/first-child])

(html/defsnippet my-donation "kickhub/html/lists.html" *donation-sel*
  [{:keys [amount]}]
  [:span] (html/content amount))

(html/defsnippet my-donations "kickhub/html/lists.html" [:#my_donations]
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
  register "kickhub/html/forms.html" [:#register] [])
(html/defsnippet ^{:doc "Snippet for the submit email form."}
  submit-email "kickhub/html/forms.html" [:#submit-email] [])
(html/defsnippet ^{:doc "Snippet for the submit project form."}
  submit-project "kickhub/html/forms.html" [:#submit-project] [])
(html/defsnippet ^{:doc "Snippet for the submit donation form."}
  submit-donation "kickhub/html/forms.html" [:#submit-donation] [])
(html/defsnippet ^{:doc "Snippet for the submit profile form."}
  submit-profile "kickhub/html/forms.html" [:#submit-profile] [])

;;; * Views

(defn index-tba-page
  "Generate the index TBA page."
  [params]
  (index-tpl {:container (concat (tba) (submit-email))
              :msg (if (:m params)
                     (str (:m params) " is now subscribed")
                     "")}))

(defn index-page
  "Generate the index page."
  [req & {:keys [msg]}]
  (let [id (friend/identity req)]
    (index-tpl {:container (news)
                :nomenu (when id "")
                :msg (or msg (if id
                               "You are now logged in"
                               "Please login or register"))})))

(defn login-page
  "Generate the login page."
  [req]
  (let [params (clojure.walk/keywordize-keys (:form-params req))
        id (friend/identity req)]
    (index-tpl {:container (if id "You are already logged in" (login))
                :msg (if (= (:login_failed params) "Y")
                       "Error when logging in..."
                       "")})))

(defn register-page
  "Generate the register page."
  [params]
  (index-tpl {:container (register)}))

(defn submit-project-page
  "Generate the page to submit a project."
  [req id]
  (let [params (clojure.walk/keywordize-keys (:form-params req))
        name (:name params)
        repo (:repo params)
        username (:current id)
        uid (get-username-uid username)]
  (if params
    (do (create-project name repo uid)
        (resp/redirect (str "/user/" username)))
    (index-tpl {:container (submit-project)}))))

(defn submit-donation-page
  "Generate the page to submit a donation."
  [req id]
  (let [params (clojure.walk/keywordize-keys (:form-params req))
        amount (:amount params)
        pid (:pid params)
        username (:current id)
        fuid (get-username-uid username)
        uid (get-pid-field pid "by")]
    (if params
      (do (create-transaction amount pid uid fuid)
          (resp/redirect (str "/user/" username)))
      (index-tpl {:container (submit-donation)}))))

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
                :nomenu (when id "")})))

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
                :nomenu (when id "")})))

(defn notfound-page "Generate the 404 page."
  [] (index-tpl {:container (notfound) :nomenu ""}))

(defn about-page "Generate the about page."
  [] (index-tpl {:container (about) :logo-link "/" :nomenu ""}))

(defn tos-page "Generate the tos page."
  [] (index-tpl {:container (tos) :nomenu ""}))

;;; * Local variables

;; Local Variables:
;; eval: (orgstruct-mode 1)
;; orgstruct-heading-prefix-regexp: ";;; "
;; End:
