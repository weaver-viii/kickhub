(ns kickhub.html.templates
  (:require
   [kickhub.model :refer :all]
   [ring.util.response :as resp]
   [net.cgrand.enlive-html :as html]))

(defmacro maybe-substitute
  ([expr] `(if-let [x# ~expr] (html/substitute x#) identity))
  ([expr & exprs] `(maybe-substitute (or ~expr ~@exprs))))

(defmacro maybe-content
  ([expr] `(if-let [x# ~expr] (html/content x#) identity))
  ([expr & exprs] `(maybe-content (or ~expr ~@exprs))))

;;; Templates

(html/deftemplate index-tpl "kickhub/html/base.html"
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

;;; Snippets

(html/defsnippet tba "kickhub/html/messages.html" [:#tba] [])
(html/defsnippet notfound "kickhub/html/messages.html" [:#notfound] [])
(html/defsnippet about "kickhub/html/messages.html" [:#about] [])
(html/defsnippet tos "kickhub/html/messages.html" [:#tos] [])

(html/defsnippet news "kickhub/html/news.html" [:#allnews] [])
(html/defsnippet profile "kickhub/html/profile.html" [:#profile] [])

;;; Projects and donations snippets

(def ^:dynamic *project-sel* [[:.project (html/nth-of-type 1)] :> html/first-child])
(html/defsnippet my-project "kickhub/html/lists.html" *project-sel*
  [{:keys [name]}]
  [:a] (html/content name))
(html/defsnippet my-projects "kickhub/html/lists.html" [:#my_projects]
  [projects]
  [:#content] (html/content (map #(my-project %) projects)))

(def ^:dynamic *donation-sel* [[:.donation (html/nth-of-type 1)] :> html/first-child])
(html/defsnippet my-donation "kickhub/html/lists.html" *donation-sel*
  [{:keys [amount]}]
  [:a] (html/content amount))
(html/defsnippet my-donations "kickhub/html/lists.html" [:#my_donations]
  [donations]
  [:#content1] (html/content (map #(my-donation %) donations)))

;; (render (html/emit* (my-projects (get-uid-projects (get-username-uid "bzg")))))
;; (render (html/emit* (map #(my-project %) (get-uid-projects (get-username-uid "bzg"))))))
;; (html/content (map #(my-project %) (get-uid-projects (get-username-uid "bzg"))))
;; (html/content (map #(my-donation %) (get-uid-transactions (get-username-uid "bzg"))))

;;; Other snippets

(html/defsnippet login "kickhub/html/forms.html" [:#login] [])
(html/defsnippet register "kickhub/html/forms.html" [:#register] [])
(html/defsnippet submit-email "kickhub/html/forms.html" [:#submit-email] [])
(html/defsnippet submit-project "kickhub/html/forms.html" [:#submit-project] [])
(html/defsnippet submit-donation "kickhub/html/forms.html" [:#submit-donation] [])
(html/defsnippet submit-profile "kickhub/html/forms.html" [:#submit-profile] [])

;;; Views

(defn index-tba-page [params]
  (index-tpl {:container (concat (tba) (submit-email))
              :msg (if (:m params)
                     (str (:m params) " is now subscribed")
                     "")}))

(defn index-page [auth?]
  (index-tpl {:container (news)
              :nomenu (when auth? "")
              :msg (if auth?
                     "You are now logged in"
                     "Please login or register")}))

(defn login-page [params]
  (index-tpl {:container (login)
              :msg (if (= (:login_failed params) "Y")
                     "Error when logging in..."
                     "")}))

(defn register-page [params]
  (index-tpl {:container (register)}))

(defn submit-project-page [req id]
  (let [params (clojure.walk/keywordize-keys (:form-params req))
        name (:name params)
        repo (:repo params)
        uid (get-username-uid (:current id))]
  (if params
    (do (create-project name repo uid))
        (resp/redirect "/user"))
    (index-tpl {:container (submit-project)})))

(defn submit-donation-page [req id]
  (let [params (clojure.walk/keywordize-keys (:form-params req))
        amount (:amount params)
        pid (:pid params)
        fuid (get-username-uid (:current id))
        uid (get-pid-field pid "by")]
    (if params
      (do (create-transaction amount pid uid fuid)
          (resp/redirect "/user"))
      (index-tpl {:container (submit-donation)}))))

(defn user-page [req id]
  (let [params (clojure.walk/keywordize-keys (:form-params req))
        uid (get-username-uid (:current id))]
    (index-tpl {:container
                (concat (my-projects (get-uid-projects uid))
                        (my-donations (get-uid-transactions uid)))})))

(defn notfound-page [] (index-tpl {:container (notfound)}))
(defn about-page [] (index-tpl {:container (about) :logo-link "/"}))
(defn tos-page [] (index-tpl {:container (tos)}))
(defn profile-page [] (index-tpl {:container (profile)}))
(defn submit-profile-page [] (index-tpl {:container (submit-profile)}))

;;; Testing

;; (defn render [t] (apply str t))
;; (render (html/emit* (my-projects "2")))
