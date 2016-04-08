(ns cleebo.backend.handlers.annotations
  (:require [re-frame.core :as re-frame]
            [schema.core :as s]
            [cleebo.shared-schemas :refer [annotation-schema]]
            [cleebo.utils :refer [update-token]]
            [cleebo.backend.middleware :refer [standard-middleware no-debug-middleware]]))

(re-frame/register-handler
 :mark-hit
 standard-middleware
 (fn [db [_ {:keys [hit-id flag]}]]
   (assoc-in db [:session :results-by-id hit-id :meta :marked] (boolean flag))))

(defn has-marked?
  "for a given hit-map we look if the current (de)marking update
  leave behind marked tokens. The result will be false if no marked
  tokens remain, otherwise it returns the token-id of the token
  being marked for debugging purposes"
  [{:keys [hit meta] :as hit-map} flag token-id]
  (if flag
    true
    (let [marked-tokens (filter :marked hit)]
      (case (count marked-tokens)
        0 (throw (js/Error. "Trying to demark a token, but no marked tokens found"))
        1 (do (assert (= token-id (:id (first marked-tokens)))) false)
        (some #(= token-id %) (map :id marked-tokens))))))

(re-frame/register-handler
 :mark-token
 standard-middleware
 (fn [db [_ {:keys [hit-id token-id flag]}]]
   (let [hit-map (get-in db [:session :results-by-id hit-id])
         has-marked (has-marked? hit-map flag token-id)
         hit-map (assoc-in hit-map [:meta :has-marked] (boolean has-marked))
         token-fn #(if flag (assoc % :marked true) (dissoc % :marked))]
     (assoc-in
      db
      [:session :results-by-id hit-id]
      (update-token hit-map token-id token-fn)))))

(re-frame/register-handler              ;todo iob annotations
 :add-annotation
 standard-middleware
 (fn [db [_ {hit-id :hit-id {{scope :scope} :span {k :key} :ann} :ann :as ann}]]
   (if-let [hit-map (get-in db [:session :results-by-id hit-id])]
     (let [token-fn (fn [token] (assoc-in token [:anns k] ann))]
       (assoc-in
        db
        [:session :results-by-id hit-id]
        (update-token hit-map (str scope) token-fn)))
     db)))

(s/defn ^:always-validate make-annotation :- annotation-schema
  [ann username token-id]
  {:ann ann
   :username username
   :span {:type "token"
          :scope token-id}
   :timestamp (.now js/Date)})

(defmulti package-annotation
  (fn [ann hit-id token-id]
    [(type ann) (type token-id)]))

(s/defmethod package-annotation
  [cljs.core/PersistentArrayMap js/Number]
  [ann hit-id :- s/Int token-id :- s/Int]
  (let [ann (make-annotation ann js/username token-id)]
    {:hit-id hit-id
     :ann ann}))

(s/defmethod package-annotation
  [cljs.core/PersistentArrayMap cljs.core/PersistentVector]
  [ann hit-ids :- [s/Int] token-ids :- [s/Int]]
  (let [anns (mapv (fn [token-id] (make-annotation ann js/username token-id)) token-ids)]
    {:hit-id hit-ids
     :ann anns}))

(s/defmethod package-annotation
  [cljs.core/PersistentVector cljs.core/PersistentVector]
  [anns hit-ids :- [s/Int] token-ids :- [s/Int]]
  {:pre [(apply = (map count [anns hit-ids]))]}
  (let [anns (mapv (fn [ann t-id] (make-annotation ann js/username t-id)) anns token-ids)]
    {:hit-id hit-ids
     :ann anns}))

(defn dispatch-annotation
  [ann hit-id token-id]
  (let [ann-map (package-annotation ann hit-id token-id)]
    (re-frame/dispatch [:ws :out {:type :annotation :data ann-map}])))

;;; todo
(s/defn ^:always-validate make-span-ann  :- annotation-schema
  [k :- s/Str v :- s/Str username :- s/Str IOB :- (s/enum :I :O :B)]
  {:ann {:key k :value {:IOB IOB :value v}}
   :username username
   :timestamp (.now js/Date)})

(s/defn ^:always-validate dispatch-span-annotation  ;redo
  [k v hit-id :- s/Int token-ids :- [s/Int]]
  (let [c (dec (count token-ids))]
    (doseq [[idx token-id] (map-indexed vector token-ids)
            :let [IOB (cond (= idx 0) :B
                            (= idx c) :O
                            :else     :I)]]
      (re-frame/dispatch
       [:ws :out {:type :annotation
                  :data {:hit-id hit-id
                         :token-id token-id
                         :ann (make-span-ann k v js/username IOB)}}]))))
