(ns cosycat.autosuggest
  (:require [reagent.core :as reagent]
            [goog.string :as gstr]
            [cosycat.utils :refer [string-contains]]
            [cosycat.components :refer [user-thumb]]
            [react-bootstrap.components :as bs]
            [react-autosuggest.core :refer [autosuggest]]))

(defn dekeywordize [k]
  (apply str (rest (str k))))

(defn denormalize-tagset [{:keys [tags]}]
  (->> (seq tags)
       (mapcat (fn [[tag keyvals]] (map #(assoc % :tag (dekeywordize tag)) keyvals)))))

(defn denormalize-tagsets [tagsets]
  (mapv (fn [{:keys [name] :as tagset}]
          {:name name
           :tags (denormalize-tagset tagset)})
        tagsets))

(defn unique-by [f coll]
  (->> (group-by f coll) (reduce-kv (fn [m k v] (assoc m k (first v))) {}) vals flatten))

(defn tags-by [by-name & {:keys [by] :or {by [:scope :tag]}}]
  (map (fn [{:keys [name tags]}]
         {:name name
          :tags (unique-by #(select-keys % by) tags)})
       by-name))

(defn filter-suggs [suggs f & {:keys [dedup-by]}]
  (->> suggs
       (map (fn [{:keys [tags] :as section}]
              (when-let [ftags (seq (filter f tags))]
                (assoc section :tags (if-not dedup-by ftags (unique-by dedup-by ftags))))))
       (filter identity)
       vec))

(defn startswith [s prefix]
  (gstr/caseInsensitiveStartsWith s prefix))

(defn tag->val
  "remap :tag key to :val key"
  [{:keys [tags] :as sugg}]
  (assoc sugg :tags (mapv (fn [{:keys [tag] :as t}]
                            (-> t (assoc :val tag) (dissoc :tag))) tags)))

(defn find-keys
  "returns suggested annotation key(s) per section"
  [s suggs & {:keys [dedup-by]}]
  (->> (if (empty? s)
         suggs
         (filter-suggs suggs (fn [{:keys [tag]}] (startswith tag s)) :dedup-by dedup-by))
       (mapv tag->val)))

(defn find-vals
  "returns suggested annotation val(s) per section"
  [skey sval suggs]
  (if (empty? sval)
    (filter-suggs suggs (fn [{:keys [tag]}] (= tag skey)))
    (filter-suggs suggs (fn [{:keys [tag val]}] (and (= tag skey) (startswith val sval))))))

(defn has-key?
  "user input has key"
  [s]
  (re-find #".*=.*" s))

(defn fetch-requested-tag
  "general suggestion f"
  [tag-suggestions suggestions & {:keys [scope]}]
  (fn [value]
    (if-not (has-key? value)
      (find-keys value tag-suggestions :dedup-by :tag)
      (let [[key val] (clojure.string/split value #"=")]
        (if (empty? (find-keys key tag-suggestions :dedup-by :tag))
          []
          (find-vals key val suggestions))))))

(defn wrap-react [my-atom f]
  (fn [arg]
    (reset! my-atom (f (.-value arg)))))

(defn on-change-suggest
  ([value-atom] (on-change-suggest value-atom identity))
  ([value-atom f]
   (fn [e new-val]
     (let [callback (or f identity)]
       (callback (.-newValue new-val))
       (reset! value-atom (.-newValue new-val))))))

(defn get-suggestion-value [value-atom]
  (fn [arg]
    (let [val (aget arg "val")]
      (if-not (has-key? @value-atom)
        val
        (str (aget arg "tag") "=" val)))))

(defn render-tag-suggestion [value-atom]
  (fn [arg]
    (let [val (aget arg "val")]
      (if (has-key? @value-atom)
        (reagent/as-element [:p val])
        (reagent/as-element [:p val])))))

(defn suggest-annotations
  [tagsets {:keys [value on-change on-key-press] :as props}]
  (let [sugg-atom (reagent/atom [])
        value-atom (or value (reagent/atom ""))]
    (fn [tagsets {:keys [on-change on-key-press] :as props}]
      (let [suggs (denormalize-tagsets tagsets)
            tag-suggs (tags-by suggs)]
        [:div.container-fluid
         [:div.row
          [autosuggest
           {:suggestions @sugg-atom
            :multiSection true
            :onSuggestionsUpdateRequested (wrap-react sugg-atom (fetch-requested-tag tag-suggs suggs))
            :onSuggestionsClearRequested #(reset! sugg-atom [])
            :getSuggestionValue (get-suggestion-value value-atom)
            :renderSuggestion (render-tag-suggestion value-atom)
            :getSectionSuggestions #(aget % "tags")
            :renderSectionTitle #(reagent/as-element [:strong (aget % "name")])
            :inputProps (merge props
                               {:onChange (on-change-suggest value-atom on-change)
                                :value @value-atom})}]]]))))

(defn filter-users [users value]
  (filter
   (fn [{:keys [firstname lastname username email]}]
     (some #(string-contains % value) [firstname lastname username email]))
   users))

(defn fetch-requested-user [users]
  (fn [value]
    (if-let [selected (seq (filter-users users value))]
      (vec selected)
      [])))

(defn render-user-suggestion [value-atom]
  (fn [arg]
    (let [{{href :href} :avatar username :username} (js->clj arg :keywordize-keys true)]
      (reagent/as-element
       [:div {:style {:margin "10px 0"}}
        [user-thumb href {:height "25px" :width "25px"}]
        [:span
         {:style {:padding-left "10px"}}
         username]]))))

(defn suggest-users [users {:keys [value on-change] :as props}]
  (let [sugg-atom (reagent/atom [])
        value-atom (or value (reagent/atom ""))]
    (fn [users {:keys [value on-change] :as props}]
      [:div.container-fluid
       [:div.row
        [autosuggest
         {:suggestions @sugg-atom
          :onSuggestionsUpdateRequested (wrap-react sugg-atom (fetch-requested-user users))
          :onSuggestionsClearRequested #(reset! sugg-atom [])
          :getSuggestionValue #(aget % "username")
          :shouldRenderSuggestions (fn [value] true)
          :renderSuggestion (render-user-suggestion value-atom)
          :inputProps (merge props
                             {:onChange (on-change-suggest value-atom on-change)
                              :value @value-atom})}]]])))
