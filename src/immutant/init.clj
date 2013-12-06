(ns immutant.init
  (:use kickhub.core)
  (:require [immutant.web :as web]))

;; Main ring handler called by Immutant
(web/start #'ring-handler)
