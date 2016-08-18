(ns cleebo.autocomplete
  (:require [goog.string :as gstr]
            [reagent.core :as reagent]
            [taoensso.timbre :as timbre]))

(def annotation-keys
  {"NE"
   ["country" "person" "organization"]
   "pos"
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

(defn parse-complex-expression [expr]
  (if (not (re-find #".*=.*" expr))
    (find-tags expr (keys annotation-keys))
    (let [[k v] (clojure.string/split expr #"=")]
      (if v
        (map #(str k "=" %) (find-tags v (get annotation-keys k [])))
        (find-tags k (keys annotation-keys))))))

(defn complex-source [req res]
  (try 
    (let [term (.-term req)]
      (res (clj->js (parse-complex-expression term))))
    (catch :default e
      (res (clj->js {})))))

(defn simple-source [& [target]]
  (fn [req res]
    (try
      (let [term (.-term req)
            tags (case target
                   :keys (keys annotation-keys)
                   :vals (annotation-keys "pos"))]
        (res (clj->js (find-tags term tags))))
      (catch :default e
        (res (clj->js {}))))))

(defn get-source-fn [source & [target]]
  (case source
    :complex-source complex-source
    :simple-source (simple-source target)
    (throw (js/Error. "Unknown autocomplete target"))))

(defn annotation-autocomplete [{:keys [id source target] :as args}]
  (reagent/create-class
   {:reagent-render (fn [args] [:div [:input (dissoc args :source :target)]])
    :component-did-mount
    (fn []
      (js/$
       (fn []
         (.autocomplete
          (js/$ (str "#" id))
          (clj->js {:source (get-source-fn source target)})))))}))

(defn find-user [term users]
  (if (empty? term)
    (map :username users)
    (->> users
         (filter (fn [{:keys [username firstname lastname email]}]
                   (some #(gstr/caseInsensitiveStartsWith % term)
                         [username firstname lastname email])))
         (map :username))))

(defn users-autocomplete [{:keys [id users]}]
  (reagent/create-class
   {:reagent-render (fn [args] [:div [:input (dissoc args :users)]])
    :component-did-mount
    (fn []
      (js/$
       (fn []
         (.autocomplete
          (js/$ (str "#" id))
          (clj->js {:source (fn [req res]
                              (try (let [term (.-term req)]
                                     (res (clj->js (find-user term users))))
                                   (catch :default e
                                     (res (clj->js {})))))})))))}))

