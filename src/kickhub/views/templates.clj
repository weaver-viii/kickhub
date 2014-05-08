(ns kickhub.views.templates
  (:require [net.cgrand.enlive-html :as html]
            [noir.session :as session]
            [kickhub.model :refer :all]))

;;; * Utility functions

(defmacro maybe-content
  "Maybe content."
  ([expr] `(if-let [x# ~expr] (html/content x#) identity))
  ([expr & exprs] `(maybe-content (or ~expr ~@exprs))))

;;; * Snippets

(html/defsnippet ^{:doc "Snippet for the login form."}
  login "kickhub/views/html/forms.html" [:#login] [])

(html/defsnippet ^{:doc "Snippet for the register form."}
  register "kickhub/views/html/forms.html" [:#register] [])

;; FIXME Continue
(html/defsnippet ^{:doc "Paypal form."}
  paypal-button "kickhub/views/html/paypal.html" [:#paypal]
  [{:keys [email]}]
  [:#business] (html/set-attr :value email)
  [:#submit] (html/content "Support"))

(html/defsnippet ^{:doc "Display a task"}
  task-item "kickhub/views/html/tables.html"
  [:#task-item]
  [{:keys [name license status uid]}]
  [:#task-name] (html/content name)
  [:#task-license] (html/content license)
  [:#task-status] (html/content status)
  [:#task-link] (html/content (paypal-button (get-uid-all uid))))

(html/defsnippet ^{:doc "Display a transaction."}
  transaction-item "kickhub/views/html/tables.html"
  [:#transaction-item]
  [{:keys [amount currency status fromuid]}]
  [:#transaction-fromuid] (html/content fromuid)
  [:#transaction-amount] (html/content amount)
  [:#transaction-currency] (html/content currency)
  [:#transaction-status] (html/content status))

(html/defsnippet ^{:doc "List of tasks."}
  tasks-list "kickhub/views/html/tables.html"
  [:#tasks-list]
  []
  [:#task-item] (html/content
                 (map #(task-item %) (get-tasks 10))))

(html/defsnippet ^{:doc "List of transactions."}
  transactions-list "kickhub/views/html/tables.html"
  [:#transactions-list]
  []
  [:#transaction-item] (html/content
                        (map #(transaction-item %)
                             (get-from-stream "transactions" 10))))

(html/defsnippet ^{:doc "Main unlogged menu"}
  unlogged-menu "kickhub/views/html/menus.html" [:#unlogged-menu] [])

(html/defsnippet ^{:doc "Main logged menu"}
  logged-menu "kickhub/views/html/menus.html" [:#logged-menu] [])

(html/defsnippet ^{:doc "Snippet for the new task form."}
  new-task "kickhub/views/html/forms.html" [:#submit-task] [])

(html/defsnippet ^{:doc "Snippet for displaying a task."}
  show-task "kickhub/views/html/tables.html" [:#task-show]
  [{:keys [name license status]}]
  [:#task-name] (html/content name)
  [:#task-license] (html/content license)
  [:#task-status] (html/content status))

;;; * Templates

(html/deftemplate ^{:doc "Main index template"}
  main-tpl "kickhub/views/html/base.html"
  [{:keys [title content menu]}]
  [:head :title] (html/content title)
  [:#menu] (if (session/get :username)
             (html/content (logged-menu))
             (html/content (unlogged-menu)))
  [:#content] (maybe-content content))

;;; * Testing

;; (defn render [t] (apply str t))
;; (render (html/emit* (task-item (first (get-from-stream "timeline" 5)))))
;; (render (html/emit* (tasks-list)))
;; (render (html/emit* (show-task {:name "bonjour"})))
;; (render (html/emit* (paypal-button (get-uid-all "1"))))

;;; * Local variables

;; Local Variables:
;; eval: (orgstruct-mode 1)
;; orgstruct-heading-prefix-regexp: ";;; "
;; End:

