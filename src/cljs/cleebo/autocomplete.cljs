(ns cleebo.autocomplete
  (:require [goog.string :as gstr]
            [reagent.core :as reagent]))

(def annotation-keys
  {"pos"
   ["NN" "NNP" "NM" "PP"]
   "lemma"
   []
   "animate"
   ["true" "false"]
   "tense"
   ["presens" "past"]})

(defn find-tags [prefix tags]
  (if (empty? prefix)
    tags
    (filter #(gstr/caseInsensitiveStartsWith % prefix) tags)))

(defn parse-expression [expr]
  (if (not (re-find #".*=.*" expr))
    (find-tags expr (keys annotation-keys))
    (let [[k v] (clojure.string/split expr #"=")]
      (if v
        (map #(str k "=" %) (find-tags v (get annotation-keys k [])))
        (find-tags k (keys annotation-keys))))))

(defn autocomplete-jq [{:keys [id] :as args-map}]
  (reagent/create-class
   {:reagent-render
    (fn [args-map]
      [:div [:input args-map]])
    :component-did-mount
    (fn []
      (js/$
       (fn []
         (.autocomplete
          (js/$ (str "#" id))
          (clj->js {:source (fn [req res]
                              (try 
                                (let [term (.-term req)]
                                  (res (clj->js (parse-expression term))))
                                (catch :default e
                                  (res (clj->js {})))))})))))}))
