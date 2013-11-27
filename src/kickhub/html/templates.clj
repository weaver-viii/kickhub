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

(html/deftemplate index-tpl "kickhub/html/base.html" [{:keys [container logo-link]}]
  [:#logo :a]   (html/set-attr :href (or logo-link "/about"))
  [:#container] (maybe-content container))

;;; Snippets

(html/defsnippet tba "kickhub/html/messages.html" [:#tba] [])
(html/defsnippet notfound "kickhub/html/messages.html" [:#notfound] [])
(html/defsnippet about "kickhub/html/messages.html" [:#about] [])

(html/defsnippet news "kickhub/html/news.html" [:#allnews] [])
(html/defsnippet profile "kickhub/html/profile.html" [:#profile] [])
(html/defsnippet my-projects "kickhub/html/lists.html" [:#my_projects] [])
(html/defsnippet my-donations "kickhub/html/lists.html" [:#my_donations] [])

(html/defsnippet login "kickhub/html/forms.html" [:#login] [])
(html/defsnippet submit-email "kickhub/html/forms.html" [:#submit-email] [])
(html/defsnippet submit-project "kickhub/html/forms.html" [:#submit-project] [])
(html/defsnippet submit-donation "kickhub/html/forms.html" [:#submit-donation] [])
(html/defsnippet submit-profile "kickhub/html/forms.html" [:#submit-profile] [])

;;; Views

(defn index-tba-page [] (index-tpl {:container (concat (tba) (submit-email))}))
(defn notfound-page [] (index-tpl {:container (notfound)}))
(defn index-page [] (index-tpl {:container (news)}))
(defn about-page [] (index-tpl {:container (about) :logo-link "/"}))
(defn login-page [] (index-tpl {:container (login)}))
(defn profile-page [] (index-tpl {:container (profile)}))
(defn submit-profile-page [] (index-tpl {:container (submit-profile)}))
(defn user-page [] (index-tpl {:container (concat (my-projects) (my-donations))}))
(defn submit-project-page [] (index-tpl {:container (submit-project)}))
(defn submit-donation-page [] (index-tpl {:container (submit-donation)}))

;;; Testing

;; (defn render [t]
;;   (apply str t))

;; (render (html/emit* (profile)))

