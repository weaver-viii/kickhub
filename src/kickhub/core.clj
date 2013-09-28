(ns kickhub.core)

(defn ring-handler [request]
  {:status 200
    :headers {"Content-Type" "text/html"}
    :body "Hello from Immutant!" })