(ns cleebo.ajax-jsonp
  (:import [goog.net Jsonp]
           [goog Uri]))

;;; shamelessly borrowed from:
;;; https://github.com/cemerick/url/blob/master/src/cemerick/url.cljx

(defn url-encode
  [string]
  (some-> string str (js/encodeURIComponent) (.replace "+" "%20")))

(defn map->query
  [m]
  (some->> (seq m)
           sort               ; sorting makes testing a lot easier :-)
           (map (fn [[k v]]
                  (.log js/console (url-encode (name k)) v)
                  [(url-encode (name k))
                   "="
                   (url-encode (str v))]))
           (interpose "&")
           flatten
           (apply str)))

(defn build-uri [base params]
  (str base "?" (map->query params)))

(defn jsonp
  "straight-forward goog-based jsonp implementation"
  [uri {:keys [handler error-handler params timeout json-callback-str]
        :or {timeout 10 json-callback-str "callback"}}]
  (let [url (build-uri uri params)
        the-jsonp (goog.net.Jsonp. (Uri. url json-callback-str))]
    (aset js/window json-callback-str handler) ;overwrite global javascript callback function
    (.setRequestTimeout the-jsonp timeout) ;by default goog.net.Jsonp timeouts after 5 secs
    (.send the-jsonp nil handler error-handler)))


