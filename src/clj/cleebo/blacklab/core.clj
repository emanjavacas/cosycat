(ns cleebo.blacklab.core
  (:require [cleebo.blacklab.paginator]
            [com.stuartsierra.component :as component])
  (:import [nl.inl.blacklab.search Searcher Hit Hits Concordance Kwic TextPatternRegex]
           [nl.inl.blacklab.search.grouping HitPropertyHitText]           
           [nl.inl.blacklab.queryParser.corpusql CorpusQueryLanguageParser]
           [nl.inl.util XmlUtil]
           [org.apache.lucene.document Document]
           [org.apache.lucene.index IndexableField]))

(set! *warn-on-reflection* true)

(defn unmod-query
  "Basic query handler without query modification"
  ^TextPatternRegex [s]
  (CorpusQueryLanguageParser/parse s))

(defn quote-query 
  "Basic query handler that translates single to double quotes"
  ^TextPatternRegex [s]
  (let [parsed-str (apply str (replace {\' \"} s))]
    (CorpusQueryLanguageParser/parse parsed-str)))

(defn- make-searcher 
  "Creates a searcher given index path. It does not close the searcher"
  ^Searcher  [^String path]
  (Searcher/open (java.io.File. path)))

(defn- run-query 
  "Runs the query and returns the general Hits object"
  ^Hits 
  ([^Searcher searcher ^String s]
   (run-query searcher s unmod-query))
  ([^Searcher searcher ^String s query-handler]
   (let [^TextPatternRegex query (query-handler s)
         ^Hits hits (.find searcher query)]
     hits)))

(defn update-range
  "Updates v applying function f to the items at the positions
  by a range. See #'range for its function signature"
  [v f & args]
  (if args
    (let [arange (apply range args)]
      (loop [cur (first arange)
             todo (next arange)
             res v]
        (if todo
          (recur
           (first todo)
           (next todo)
           (assoc res cur (f (get res cur))))
          res)))
    v))

(defn- make-hit-map
  "Base handler that takes a Hit and gives a clojure data struct"
  [^Hit -hit ^Hits -hits]
  (let [kwic (.getKwic -hits -hit)
        props (map keyword (.getProperties kwic))
        tokens (.getTokens kwic)
        hit (mapv (partial zipmap props) (partition (count props) tokens))]
    {:-hit -hit :-hits -hits :hit hit}))

(defn wrap-clean
  "Basic handler that removes the hit key from hit-map"
;  [handler]
  [hit-map] (dissoc hit-map :-hit :-hits))

(defn wrap-doc-by-name
  "Handler for extracting doc metadata"
  [handler]
  (fn [hit-map ^Searcher searcher field-name]
    (let [^Hit -hit (:-hit hit-map)
          ^Document doc (.document searcher (.doc -hit))
          ^String field (.stringValue (.getField doc field-name))
          new-map (assoc-in hit-map [:meta (keyword field-name)] field)]
      (handler new-map))))

(defn wrap-doc-by-names
  "Extract multiple fields at once"
  [handler]
  (fn [hit-map ^Searcher searcher & field-names]
    (let [^Hit -hit (:-hit hit-map)
          ^Document doc (.document searcher (.doc -hit))
          get-value (fn [field-name] (.stringValue (.getField doc field-name)))
          fields (zipmap (map keyword field-names) (map get-value field-names))
          new-map (assoc hit-map :meta fields)]
      (handler new-map))))

(defn wrap-doc
  "Extract all doc fields"
  [handler ^Searcher searcher]
  (fn [hit-map]
    (let [^Hit -hit (:-hit hit-map)
          ^Document doc (.document searcher (.doc -hit))
          field-tokens (map (fn [^IndexableField field]
                              [(.name field)
                               (.stringValue field)])
                            (.getFields doc))
          fields (interleave (map keyword (map first field-tokens))
                             (map second field-tokens))]
      (handler (apply update-in hit-map [:meta] assoc fields)))))

(defn wrap-match
  "Add match annotation to match tokens"
  [handler]
  (fn [hit-map]
    (let [^Hit -hit (:-hit hit-map)
          ^Hits -hits (:-hits hit-map)
          ^Kwic kwic (.getKwic -hits -hit)
          start (.getHitStart kwic)
          end (.getHitEnd kwic)
          hit-vec (:hit hit-map)
          hit-match (update-range hit-vec #(assoc % :match true) start (inc end))
          new-map (assoc hit-map :hit hit-match)]
      (handler new-map))))

(defn wrap-hit [handler]
  (fn [hit]
    (handler hit)))

(defn hits-handler [hits searcher]
  (let [handler (wrap-hit (->  wrap-clean
                              wrap-match
                              (wrap-doc searcher)))]
    (for [^Hit hit hits
          :let [hit-map (make-hit-map hit hits)]]
      (handler hit-map))))

(defn- -hits->page [^Hits -hits from to context]
  (.setContextSize -hits context)
  (.window -hits from to))

(defprotocol SearcherState
  (update-hits! [searcher query-id -new-hits]))

(defrecord BLSearcher [searchers -hits hits-handler]
  component/Lifecycle
  (start [component] component)
  (stop [component] component)
  SearcherState
  (update-hits! [seacher query-id -new-hits]
    (swap! -hits assoc query-id -new-hits)))

(defn new-blsearcher [paths-map]
  (let [searchers (zipmap (keys paths-map) (map make-searcher (vals paths-map)))]
    (map->BLSearcher
     {:searchers searchers
      :-hits (atom {})
      :hits-handler hits-handler})))

(defn- get-hits
  ([searcher corpus]
   (get-hits searcher corpus "default"))
  ([searcher corpus query-id]
   (get @(:-hits searcher) query-id)))

(defn bl-query-size
  ([searcher corpus]
   (bl-query-size searcher corpus "default"))
  ([searcher corpus query-id]
   (let [-hits (get-hits searcher query-id)]
     (.size ^Hits -hits))))

(defn bl-query
  ([searcher corpus query-str from to context]
   (bl-query searcher corpus query-str from to context "default"))
  ([searcher corpus query-str from to context query-id]
   (let [blsearcher (get-in searcher [:searchers corpus])
         hits-handler (get searcher :hits-handler)
         -hits (run-query blsearcher query-str)         
         hits (hits-handler (-hits->page -hits from to context) blsearcher)]
     (update-hits! searcher query-id -hits)
     hits
     {:results  (map (fn [hit num] (assoc hit :num num)) hits (range from to))
      :from from
      :to to
      :query-str query-str
      :query-size (.size ^Hits -hits)})))

(defn bl-query-range
  ([searcher corpus from to context]
   (bl-query-range searcher corpus from to context "default"))
  ([searcher corpus from to context query-id]
   (let [blsearcher (get-in searcher [:searchers corpus])
         hits-handler (get searcher :hits-handler)
         -hits (get-hits searcher corpus query-id)
         hits (hits-handler (-hits->page -hits from to context) blsearcher)]
     {:results  (map (fn [hit num] (assoc hit :num num)) hits (range from to))
      :from from
      :to to})))

(def paths-map {"brown"    "/home/enrique/code/BlackLab/brown-index/"
                "brown-id" "/home/enrique/code/BlackLab/brown-index-id/"})

;; ;;;
(def searcher (new-blsearcher paths-map))
(def hits (bl-query searcher "brown-id" "[pos=\".*\"]" 0 10 5))
(bl-query-range searcher "brown-id" 0 10 5)
;; (first (:results hits))
;(query-range searcher "brown-id" 0 50 5)

;; (raw-text hits :n 10)
;; (query-size searcher "brown")

;; (map :word (filter :match ))

;; (map :hit-vec (take 10 hits))


;; (defn raw-text [hits & {:keys [n] :or {n 10}}]
;;   (map :word (filter :match (map :hit-vec (take n hits)))))

;; (def hits (morph-query "[pos=\"N.*\"] "))
;; (def hit-property (HitPropertyHitText. hits "contents"))
;; (.sort hits (HitPropertyHitText. hits "contents"))
