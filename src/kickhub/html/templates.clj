(ns kickhub.html.templates
  (:require
   [kickhub.model :refer :all]
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
(html/defsnippet my-projects "kickhub/html/lists.html" [:#my_projects] [])
(html/defsnippet my-donations "kickhub/html/lists.html" [:#my_donations] [])

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

(defn notfound-page [] (index-tpl {:container (notfound)}))
(defn about-page [] (index-tpl {:container (about) :logo-link "/"}))
(defn tos-page [] (index-tpl {:container (tos)}))
(defn profile-page [] (index-tpl {:container (profile)}))
(defn submit-profile-page [] (index-tpl {:container (submit-profile)}))
(defn user-page [] (index-tpl {:container (concat (my-projects) (my-donations))}))
(defn submit-project-page [] (index-tpl {:container (submit-project)}))
(defn submit-donation-page [] (index-tpl {:container (submit-donation)}))

;;; Testing

;; (defn render [t] (apply str t))
;; (render (html/emit* (profile)))
