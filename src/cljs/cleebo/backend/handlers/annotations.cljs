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

(re-frame/register-handler
 :add-annotation
 standard-middleware
 (fn [db [_ {:keys [hit-id token-id anns]}]]
   (if-let [hit-map (get-in db [:session :results-by-id hit-id])]
     (let [token-fn (fn [token] (assoc token :anns anns))]
       (assoc-in
        db
        [:session :results-by-id hit-id]
        (update-token hit-map (str token-id) token-fn)))
     db)))

(s/defn ^:always-validate make-ann :- annotation-schema
  [k v username]
  {:ann {:key k :value v}
   :username username
   :timestamp (.now js/Date)})

(def cljs-vec cljs.core/PersistentVector)
(defmulti process-annotation
  (fn [k v hit-id token-id]
    [(type k) (type v)]))

(defmethod process-annotation [js/String js/String]
  [k v hit-id token-id]
  (let [ann (make-ann k v js/username)]
    {:hit-id hit-id
     :token-id token-id
     :ann ann}))

(defmethod process-annotation [cljs-vec cljs-vec]
  [k v hit-id token-id]
  {:pre [(apply = (map count [k v hit-id token-id]))]}
  (let [anns (mapv (fn [k v] (make-ann k v js/username)) k v)]
    {:hit-id hit-id
     :token-id token-id
     :ann anns}))

(defn dispatch-annotation [k v hit-id token-id]
  (let [ann-map (process-annotation k v hit-id token-id)]
    (re-frame/dispatch
     [:ws :out {:type :annotation
                :data ann-map}])))

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
