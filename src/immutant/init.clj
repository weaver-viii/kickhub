(ns immutant.init
  (:use kickhub.core)
  (:require [immutant.web :as web]))

(web/start #'ring-handler)