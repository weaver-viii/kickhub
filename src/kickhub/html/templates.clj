(ns kickhub.html.templates
  (:require
   [kickhub.model :refer :all]
   [net.cgrand.enlive-html :as html]))

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
   (map #(link-model
          {:text (:name %)
           :href (str "user/" (get-uid-field (:by %) "u") "/" (:name %))})
        latest-projects)))

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
  [:#login :#githublogin] (html/content (str "Github login: " u))
  [:#login :#khsince] (html/content (str "On KickHub since: " (str created)))
  [:#active-projects] 
  (html/content
   (map #(link-model {:text (:name %) :href (str "/user/" u "/" (:name %))})
        user-projects)))

(html/deftemplate projecttpl "kickhub/html/project.html"
  [{:keys [name created]}]
  [:#login :#pname] (html/content (str "Project name: " name))
  [:#login :#khsince] (html/content (str "On KickHub since: " (str created))))

(html/deftemplate abouttpl "kickhub/html/about.html" [])
(html/deftemplate roadmaptpl "kickhub/html/roadmap.html" [])
(html/deftemplate notfoundtpl "kickhub/html/notfound.html" [])

