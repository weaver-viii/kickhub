(ns kickhub.rss
  (:require [clj-rss.core :as rss]
            [taoensso.carmine :as car]
            [kickhub.model :refer :all]))

;;; * Global RSS feed

(defn rss []
  (apply rss/channel-xml
         {:title "KickHub"
          :link "http://kickhub.com"
          :description "KickHub: New Supported Free Softwares"}
         (filter #(not (empty? %))
                 (map #(news-to-rss-item %) (get-news)))))

(defrecord rss-item [title link description])

(defn news-to-rss-item
  "Given a news id `nid`, maybe export the news to a rss item."
  [nid]
  (let [nparams
        (keywordize-array-mapize
         (wcar* (car/hgetall (str "nid:" nid))))]
    (if (= (:t nparams) "p")
      (let [pname (get-pid-field (:pid nparams) "name")]
        (->rss-item (str "New project: " pname)
                    (str "http://localhost:8080/project/" pname)
                    (format
                     "%s published a new project: \"%s\""
                     (get-uid-field
                      (get-pid-field (:pid nparams) "by") "u")
                     pname))))))

;;; * Local variables

;; Local Variables:
;; eval: (orgstruct-mode 1)
;; orgstruct-heading-prefix-regexp: ";;; "
;; End:
