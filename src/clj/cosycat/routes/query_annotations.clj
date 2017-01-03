(ns cosycat.routes.query-annotations
  (:require [monger.operators :refer :all]
            [cosycat.app-utils :refer [make-hit-id normalize-by]]
            [cosycat.routes.utils :refer [unwrap-arraymap]]
            [cosycat.utils :refer [->int ->long]]
            [cosycat.db.annotations :as anns]))

(defn same-doc?
  "annotation may have a doc field, which can be used to shortcut
  further proximity computations"
  [{from-doc :doc} {to-doc :doc}]
  (or (and (not from-doc) (not to-doc)) (and from-doc to-doc) (= from-doc to-doc)))

(defn span-offset
  "assumes second argument correspondes to an annotation located later in the corpus"
  [{{{from-O :O :as from-scope} :scope from-type :type :as from-span} :span from-corpus :corpus}
   {{{to-O :O :as to-scope} :scope to-type :type :as to-span} :span to-corpus :corpus}]
  (if-not (and (same-doc? from-span to-span) (= from-corpus to-corpus))
    -1
    (- (or to-O to-scope) (or from-O from-scope))))

(defn get-token-scope
  "get the scope of a given annotation"
  [{{{O :O :as scope} :scope doc :doc} :span}]
  (or O scope))

(defn get-hit-id [anns]
  (let [doc (-> anns first (get-in [:span :doc]))
        token-ids (->> anns (map get-token-scope) (sort))
        first-token (first token-ids)]
    (if (= 1 (count token-ids))
      (make-hit-id doc first-token (inc first-token))
      (let [last-token (max (inc first-token) (last token-ids))]
        (make-hit-id doc first-token)))))

(defn get-corpus [anns]
  (-> anns first (get :corpus)))

(defn normalize-group
  "transform a group of annotations into an structured map with contextual info"
  [group]
  (-> {:hit-id (get-hit-id group)
       :corpus (get-corpus group)}
      (assoc :anns group)))

(defn group-by-hits
  "group annotations in spans of at most `context` token positions. 
   output is normalized according to `normalize-group`"
  [annotations context]
  (if (empty? annotations)
    {}
    (loop [pivot (first annotations)
           queue (next annotations)
           group [(first annotations)]
           acc []]
      (if (nil? queue)
        (conj acc (normalize-group group))
        (let [offset (span-offset pivot (first queue))
              current (first queue)
              rest-q (next queue)]
          (if (and (not (neg? offset)) (< offset context))
            (recur pivot rest-q (conj group current) acc)
            (recur current rest-q [current] (conj acc (normalize-group group)))))))))

(defn type-check-query-map
  "check and conform query map fields to their right types"
  [{{ann-key :key ann-value :value} :ann username :username corpus :corpus
    {:keys [from to] :as timestamp} :timestamp :as query-map}]
  (cond-> query-map
    corpus (assoc :corpus (unwrap-arraymap corpus))
    username (assoc :username (unwrap-arraymap username))
    from (assoc-in [:timestamp :from] (->long from))
    to (assoc-in [:timestamp :to] (->long to))))

(defn build-query-map
  "thread a base query-map through a sequence of conditional statements
   transforming API input into mongodb query syntax"
  [{{{ann-key :string key-as-regex? :as-regex?} :key
     {ann-value :string value-as-regex? :as-regex?} :value} :ann
    username :username corpus :corpus
    {:keys [from to] :as timestamp} :timestamp}]
  (cond-> {}
    ann-key (assoc "ann.key" ann-key)
    (and ann-key key-as-regex?) (assoc "ann.key" {$regex ann-key})
    ann-value (assoc "ann.value" ann-value)
    (and ann-value value-as-regex?) (assoc "ann.value" {$regex ann-value})
    corpus (assoc :corpus {$in corpus})
    username (assoc :username {$in username})
    (and from to) (assoc $and [{:timestamp {$gte from}} {:timestamp {$lt to}}])
    (and from (nil? to)) (assoc :timestamp {$gte from})
    (and to (nil? from)) (assoc :timestamp {$lt to})))

(defn query-annotations
  ([{db-conn :db :as db} project-name page-num page-size query-map]
   (anns/query-annotations db project-name query-map page-num page-size))
  ([{db-conn :db :as db} project-name page-num page-size query-map context]
   (let [page-num (->int page-num), page-size (->int page-size), context (->int context)
         typed-query-map (type-check-query-map query-map)
         query-map (build-query-map typed-query-map)
         annotations (query-annotations db project-name page-num page-size query-map)
         grouped-by-hits (group-by-hits annotations context)]
     {:page {:num-hits (count grouped-by-hits)
             :page-num page-num
             :page-size (count annotations)}
      :context context
      :size page-size
      :query-size (anns/count-annotation-query db project-name query-map)
      :query-map typed-query-map
      :grouped-data (normalize-by grouped-by-hits :hit-id)})))
