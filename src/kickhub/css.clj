(ns kickhub.css
  (:require [garden.core :refer :all]
            [garden.color :as color]))

;;; * Variables

(def ^:private css-path-prefix (System/getenv "kickhub_path"))
(def ^:private warning (color/hsl 0 100 50))

;;; * body

(def ^:private body
  [:body
   {:margin "0"
    :padding "0"
    ;; :font-family "Monospace"
    :font-size "15pt"}])

;;; * Container

(def ^:private container
  [:#container
   {:padding-top "3em"
    ;; :text-align "center"
    :width "70%"
    :margin "auto"}
   ])

(def ^:private input-group
  [:.input-group {:max-width "60%" :margin "auto"}])

(def ^:private btn
  [:.btn-info {:margin "1.4em"}])

;;; * lists

(def ^:private li
  [:li :input {:list-style-type "none"}])

;;; * Menu

(def ^:private menu
  [:#menu [:li {:display "inline"}]
   {:position "fixed"
    :top ".8em"
    :right ".8em"}
   ])

;;; * forms

(def ^:private input
  [:input
   {:display "block"
    :padding ".4em"
    :margin ".4em"
    :background-color "#eee"
    :border "1px solid gray"
    }])

;;; * logo

(def ^:private logo
  [:logo :img
   {:position "absolute"
    :width "120px"
    :top ".3em"
    :left ".3em"
    }])

;;; * Various

(def ^:private msg
  [:#msg
   {:padding-top "1em"
    :text-align "center"
    :width "100%"
    :margin "auto"
    :color warning}])

(def ^:private notifications
  [:#notifications {:display "inline"}])

(def ^:private info
  [:#info {:color warning}])

(def ^:private news
  [:.news {:display "block"}])

;;; * Footer

(def ^:private footer
  [:#footer
   {:text-align "center"
    :position "fixed"
    :background-color "#eee"
    :border-top "1px solid gray"
    :width "100%"
    :padding ".2em"
    :bottom "0"
    }])

;;; Output to kickhub.css

(css {:output-to (str css-path-prefix "resources/public/css/kickhub.css")
      :pretty-print? false}
     body
     container
     menu
     li
     input
     input-group
     btn
     logo
     msg
     notifications
     info
     news
     footer
)

;;; * Local variables

;; Local Variables:
;; eval: (orgstruct-mode 1)
;; orgstruct-heading-prefix-regexp: ";;; "
;; End:
