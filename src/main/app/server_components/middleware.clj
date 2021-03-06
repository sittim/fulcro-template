(ns app.server-components.middleware
  (:require
    [clojure.string :as str]
    [app.server-components.config :refer [config]]
    [app.server-components.pathom :refer [parser]]
    [com.fulcrologic.fulcro.networking.file-upload :as file-upload]
    [mount.core :refer [defstate]]
    [hiccup.page :refer [html5]]
    [com.fulcrologic.fulcro.server.api-middleware :refer [handle-api-request
                                                          wrap-transit-params
                                                          wrap-transit-response]]
    [ring.middleware.defaults :refer [wrap-defaults]]
    [ring.util.response :as resp :refer [response
                                         file-response
                                         resource-response]]
    [taoensso.timbre :as log]
    [ring.middleware.session :refer [wrap-session]]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.not-modified :refer [wrap-not-modified]]
    [ring.middleware.resource :refer [wrap-resource]]
    [clojure.pprint :refer [pprint]]))

; ------------------------------------------------------------------------------
(def ^:private not-found-handler
  (fn [req]
    {:status  404
     :headers {"Content-Type" "text/plain"}
     :body    "NOPE"}))

; ------------------------------------------------------------------------------
(defn wrap-api [handler uri]
  (fn [request]
    (if (= uri (:uri request))
      (handle-api-request (:transit-params request)
        (fn [query]
          (parser {:ring/request request}
            query)))
      (handler request))))

;; ================================================================================
;; Dynamically generated HTML. We do this so we can safely embed the CSRF token
;; in a js var for use by the client.
;; ================================================================================
(defn index [csrf-token]
  (log/debug "Serving index.html")
  (html5
    [:html {:lang "en"}
     [:head {:lang "en"}
      [:title "Application"]
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
      [:link {:href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css"
              :rel  "stylesheet"}]
      [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
      [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]]
     [:body
      [:div#app]
      [:script {:src "js/main/main.js"}]]]))

;; =============================================================================
;; Account conformation page, renders with user click on the account
;; confirmation email link
;; =============================================================================
(defn account-configuration-email [csrf-token uri]
  (log/debug "Confirm Email Render page")
  (prn "URI: \"" uri "\"")
  (html5
    [:html {:lang "en"}
     [:head {:lang "en"}
      [:title "Application"]
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
      [:link {:href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css"
              :rel  "stylesheet"}]
      [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
      [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]]
     [:body
      [:div (str "This is account confirmation email")]]]))
      ;[:div#app]
      ;[:script {:src "js/main/main.js"}]]]))

;; ================================================================================
;; Workspaces can be accessed via shadow's http server on http://localhost:8023/workspaces.html
;; but that will not allow full-stack fulcro cards to talk to your server. This
;; page embeds the CSRF token, and is at `/wslive.html` on your server (i.e. port 3000).
;; ================================================================================
;(defn wslive [csrf-token]
  ;(log/debug "Serving wslive.html")
  ;(html5
    ;[:html {:lang "en"}
     ;[:head {:lang "en"}
      ;[:title "devcards"]
      ;[:meta {:charset "utf-8"}]
      ;[:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
      ;[:link {:href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css"
              ;:rel  "stylesheet"}]
      ;[:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
      ;[:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]]
     ;[:body
      ;[:div#app]
      ;[:script {:src "workspaces/js/main.js"}]]]))

; ------------------------------------------------------------------------------
(defn wrap-html-routes [ring-handler]
  (fn [{:keys [uri anti-forgery-token] :as req}]
    (prn "URI:")
    (pprint uri)
    (prn "URI:")
    (if (or (str/starts-with? uri "/api")
          (str/starts-with? uri "/images")
          (str/starts-with? uri "/files")
          (str/starts-with? uri "/js"))
      (ring-handler req)

      (if (str/starts-with? uri "/email")
        (-> (resp/response (account-configuration-email anti-forgery-token uri))
          (resp/content-type "text/html"))
        (-> (resp/response (index anti-forgery-token))
          (resp/content-type "text/html"))))))

; ------------------------------------------------------------------------------
(defstate middleware
  :start
  (let [defaults-config (:ring.middleware/defaults-config config)]
    (-> not-found-handler
      (wrap-api "/api")
      (file-upload/wrap-mutation-file-uploads {})
      (wrap-transit-params {})
      (wrap-transit-response {})
      (wrap-session)
      (wrap-resource "public")
      wrap-content-type
      wrap-not-modified
      (wrap-html-routes)
      (wrap-defaults defaults-config))))

