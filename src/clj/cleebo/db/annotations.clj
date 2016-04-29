(ns cleebo.db.annotations
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cleebo.utils :refer [get-token-id ->int]]
            [cleebo.schemas.annotation-schemas :refer [annotation-schema cpos-anns-schema]]
            [cleebo.components.db :refer [new-db colls]]
            [cleebo.db.projects :refer [new-project find-project]]
            [cleebo.roles :refer [check-annotation-update-role]]
            [schema.coerce :as coerce]))

(defn check-span-overlap
  "checks if two span annotations overlap returning false if there is overlap"
  [{{{new-B :B new-O :O} :scope} :span :as new-ann}
   {{{old-B :B old-O :O} :scope} :span :as old-ann}]
  (cond
    (and (= new-B old-B) (= new-O old-O))   true
    (and (<= new-B old-O) (<= old-B new-O)) false
    :else true))

(defmulti find-ann-id
  "fetchs id of ann in coll `anns` for given a new annotation,
  span annotations are fetched according to B-cpos"
  (fn [db {{type :type} :span}] type))

(defmethod find-ann-id "token"
  [{db :db} {{scope :scope} :span project :project {k :key} :ann}]
  (if-let [{{old-scope :scope} :span}
           (mc/find-one-as-map
            db (:anns colls)
            {"ann.key" k
             "project" project
             $and [{"span.scope.B" {$lte scope}} {"span.scope.O" {$gte scope}}]})]
    (throw (ex-info "Attempt to overwrite span ann with token ann" {:scope old-scope}))
    (-> (mc/find-one-as-map
         db (:cpos-anns colls)
         {:_id scope
          "anns.key" k
          "anns.project" project}
         {"anns.$.key" true "_id" false})
        :anns
        first)))

(defmethod find-ann-id "IOB"
  [{db :db} {{{new-B :B new-O :O :as new-scope} :scope} :span
             project :project {k :key} :ann :as ann-map}]
  (when-let [{{scope :scope} :span}
             (mc/find-one-as-map
              db (:anns colls)
              {"ann.key" k
               "project" project
               "span.scope" {$in (range new-B (inc new-O))}})]
    (throw (ex-info "Attempt to overwrite token ann with span ann" {:scope scope})))
  (when-let [{{{old-B :B old-O :O :as old-scope} :scope} :span ann-id :_id}
             (mc/find-one-as-map
              db (:anns colls)
              {"ann.key" k
               "project" project
               $and [{"span.scope.B" {$lte new-O}} {"span.scope.O" {$gte new-B}}]})]
    (if-not (and (= old-B new-B) (= old-O new-O))
      (throw (ex-info "Overlapping span" {:old-scope old-scope :new-scope new-scope}))
      {:ann-id ann-id})))

(s/defn find-ann-by-id :- annotation-schema
  [{db :db} ann-id]
  (mc/find-one-as-map db (:anns colls) {:_id ann-id} {:_id false}))

(defmulti insert-annotation
  "creates new ann in coll `anns`+`cpos-anns` for given ann"
  (fn [db {{type :type} :span}] type))

(defn insert-cpos-anns
  [{db :db} token-id {:keys [key ann-id project] :as ann}]
  (mc/find-and-modify
   db (:cpos-anns colls)
   {:_id token-id}
   {$push {:anns ann}}
   {:upsert true}))

(s/defmethod insert-annotation "token"
  :- annotation-schema
  [{db-conn :db :as db} {{scope :scope} :span project-name :project {k :key} :ann :as ann}]
  (let [{:keys [_id] :as ann} (mc/insert-and-return db-conn (:anns colls) ann)]
    (do (insert-cpos-anns db scope {:key k :ann-id _id :project project-name}))
    ann))

(s/defmethod insert-annotation "IOB"
  :- annotation-schema
  [{db-conn :db :as db} {{{B :B O :O} :scope} :span project :project {k :key} :ann :as ann}]
  (let [{:keys [_id] :as ann} (mc/insert-and-return db-conn (:anns colls) ann)]
    (doseq [token-id (range B (inc O))]
      (insert-cpos-anns db token-id {:key k :ann-id _id :project project}))
    ann))

(defn compute-history [ann]
  (let [record-ann (select-keys ann [:ann :username :timestamp :project])]
    (conj (:history ann) record-ann)))

(defmulti update-annotation
  "updates existing ann in coll `anns` for given key."
  (fn [db {{span-type :type} :span :as ann-map} ann-id] span-type))

(s/defmethod update-annotation "token"
  :- annotation-schema
  [{db-conn :db :as db}
   {timestamp :timestamp
    username :username
    project :project
    {scope :scope} :span
    {k :key v :value} :ann :as ann} ann-id]
  (mc/find-and-modify
   db-conn (:anns colls)
   {:_id ann-id}
   {$set {"ann.value" v "ann.key" k
          "span.type" "token" "span.scope" scope
          "username" username "timestamp" timestamp
          "history" (compute-history (find-ann-by-id db ann-id))}}
   {:return-new true}))

(s/defmethod update-annotation "IOB"
  :- annotation-schema
  [{db-conn :db :as db}
   {timestamp :timestamp
    username :username
    {{B :B O :O :as scope} :scope :as span} :span
    {k :key v :value} :ann :as ann}
   ann-id]
  (mc/find-and-modify
   db-conn (:anns colls)
   {:_id ann-id}
   {$set {"ann.value" v "ann.key" k
          "span.type" "IOB" "span.scope" scope
          "username" username "timestamp" timestamp
          "history" (compute-history (find-ann-by-id db ann-id))}}
   {:return-new true}))

(defn find-role
  "helper function to extract a user's role from the :users project's field"
  [project-users username]
  {:post [(not (nil? %))]}
  (:role (first (filter #(= username (:username %)) project-users))))

(defn attempt-annotation-update
  "update authorization middleware to prevent update attempts by non-authorized users"
  [db {username :username project-name :project :as ann-map} ann-id]
  (let [{project-creator :creator project-users :users} (find-project db project-name)
        role (if (= username project-creator) "creator" (find-role project-users username))]
    (if (check-annotation-update-role :update role)
      (update-annotation db ann-map ann-id)
      (throw (ex-info "Unauthorized update attempt" {:cause :wrong-update})))))

(s/defn ^:always-validate new-token-annotation
  [db {span :span :as ann-map} :- annotation-schema]
  (-> (if-let [{:keys [ann-id]} (find-ann-id db ann-map)]
        (attempt-annotation-update db ann-map ann-id)
        (insert-annotation db ann-map))
      (dissoc :_id)))

(s/defn ^:always-validate fetch-anns :- (s/maybe {s/Int {s/Str {s/Str annotation-schema}}})
  "{token-id {ann-key1 ann ann-key2 ann} token-id2 ...}.
   [enhance] a single span annotation will be fetched as many times as tokens it spans"
  [db ann-ids]
  (apply merge-with conj
         (for [{token-id :_id anns :anns} ann-ids
               {k :key ann-id :ann-id} anns
               :let [{project :project :as ann} (find-ann-by-id db ann-id)]]
           {token-id {project {k ann}}})))

(s/defn find-ann-ids-in-range
  "Given a token range and user projects returns the ann-ids of all annotations
   in that range for that user"
  [{db :db} user-projects id-from id-to] :- [cpos-anns-schema]
  (mc/find-maps
   db (:cpos-anns colls)
   {"anns.project" {$in user-projects}
    $and [{:_id {$gte id-from}} {:_id {$lt id-to}}]}))

(s/defn ^:always-validate fetch-anns-in-range
  "Given a token range and user projects returns all annotations in that range for that user"
  ([db user-projects token-id :- s/Int]
   (fetch-anns-in-range db user-projects token-id (inc token-id)))
  ([db user-projects  id-from :- s/Int id-to :- s/Int]
   (let [ann-ids (find-ann-ids-in-range db user-projects id-from id-to)]
     (fetch-anns db ann-ids))))

(defn find-first-id
  "finds first non-dummy token (token with non negative id)"
  [hit]
  (first (drop-while #(neg? %) (map get-token-id hit))))

(defn merge-annotations-hit
  [hit anns-in-range]
  (map (fn [token]
         (let [id (get-token-id token)]
           (if-let [anns (get anns-in-range id)]
             (assoc token :anns anns)
             token)))
       hit))

(defn merge-annotations
  "collect stored annotations for a given span of hits. Annotations are 
  collected at one for a given hit, since we know the token-id range of its
  tokens `from`: `to`"
  [db results project-names]
  (for [{:keys [hit] :as hit-map} results
        :let [from (find-first-id hit) ;todo, find first real id (avoid dummies)
              to   (find-first-id (reverse hit))
              anns-in-range (fetch-anns-in-range db project-names from to)
              new-hit (merge-annotations-hit hit anns-in-range)]]
    (assoc hit-map :hit new-hit)))

