(ns kickhub.html.templates
  (:require
   [kickhub.model :refer :all]
   [net.cgrand.enlive-html :as html]))

;; Shamelessly taken from Swannodette enlive tutorial
(def ^:dynamic *link-sel*
  [[:.content (html/nth-of-type 1)] :> html/first-child])

(def ^:dynamic *link-sel2*
  [[:.tcontent (html/nth-of-type 1)] :> html/first-child])

(html/defsnippet link-model-index "kickhub/html/index.html" *link-sel*
  [{:keys [by uhref project phref]}]
  [:a#user] (html/do->
             (html/content by)
             (html/set-attr :href uhref))
  [:a#project] (html/do->
                (html/content project)
                (html/set-attr :href phref)))

(html/defsnippet link-model-trans "kickhub/html/index.html" *link-sel2*
  [{:keys [tby tto bhref thref tproject phref tamount]}]
  [:a#tuser] (html/do->
              (html/content tby)
              (html/set-attr :href bhref))
  [:span#amount] (html/content tamount)
  [:a#duser] (html/do->
              (html/content tto)
              (html/set-attr :href thref))
  [:a#tproject] (html/do->
                 (html/content tproject)
                 (html/set-attr :href phref)))

(html/defsnippet link-model-user "kickhub/html/user.html" *link-sel*
  [{:keys [text href]}]
  [:a] (html/do->
        (html/content text)
        (html/set-attr :href href)))

(html/deftemplate indextpl "kickhub/html/index.html"
  [{:keys [logged link pic add latest-projects latest-transactions msg]}]
  [:#login :a.logged] (html/do-> (html/content logged) (html/set-attr :href link))
  [:#login :a.addproject] (html/content add)
  [:#login :img.pic] (html/set-attr :src pic)
  [:p.msg] (html/content msg)
  [:#new-projects]
  (html/content
   (map #(link-model-index
          {:by (get-uid-field (:by %) "u")
           :uhref (str "user/" (get-uid-field (:by %) "u"))
           :project (:name %)
           :phref (str "user/" (get-uid-field (:by %) "u") "/" (:name %))})
        latest-projects))
  [:#new-projects2]
  (html/content
   (map #(link-model-trans
          {:tby (:by %)
           :tto (:to %)
           :bhref (str "user/" (:by %))
           :thref (str "user/" (:to %))
           :tproject (get-pid-field (:for %) "name")
           :phref (str "user/" (:to %) "/" (get-pid-field (:for %) "name"))
           :tamount (:amount %)})
        latest-transactions)))

(html/defsnippet option-model "kickhub/html/addproject.html" [:option]
  [{:keys [text value]}]
  [:option] (html/do->
             (html/content text)
             (html/set-attr :value value)))

(html/deftemplate addprojecttpl "kickhub/html/addproject.html"
  [{:keys [logged link pic add repos uid]}]
  [:#login :a.logged] (html/do-> (html/content logged) (html/set-attr :href link))
  [:#login :a.addproject] (html/content add)
  [:#login :img.pic] (html/set-attr :src pic)
  [:#new-projects :form :#huid] (html/set-attr :value uid)
  [:#new-projects :form :select]
  (html/content
   (map #(option-model {:text (:name %) :value (:name %)}) repos)))

(html/deftemplate usertpl "kickhub/html/user.html"
  [{:keys [u e created user-projects picurl]}]
  [:#login :#pic] (html/set-attr :src picurl)
  [:#login :#githublogin] (html/content (str u " on Github"))
  [:#login :#khsince] (html/content (str "Since: " (str created)))
  [:#active-projects]
  (html/content
   (map #(link-model-user {:text (:name %) :href (str "/user/" u "/" (:name %))})
        (reverse user-projects))))

(html/deftemplate projecttpl "kickhub/html/project.html"
  [{:keys [name created by pid]}]
  [:#login :#pname] (html/content name)
  [:#login :#by] (html/content (str "By " (get-uid-field by "u")))
  [:#login :#khsince] (html/content (str "Since: " (str created)))
  [:#new-projects :form :input#puid] (html/set-attr :value pid)
  [:#new-projects :form :input#huid] (html/set-attr :value (get-uid-field by "u")))

(html/deftemplate abouttpl "kickhub/html/about.html" [])
(html/deftemplate roadmaptpl "kickhub/html/roadmap.html" [])
(html/deftemplate notfoundtpl "kickhub/html/notfound.html" [])
