(ns cleebo.blacklab.core
  (:require [cleebo.blacklab.paginator]
            [com.stuartsierra.component :as component])
  (:import [nl.inl.blacklab.search Searcher Hit Hits Concordance Kwic TextPatternRegex]
           [nl.inl.blacklab.search.grouping
            HitProperty HitPropertyHitText HitPropertyLeftContext HitPropertyRightContext]
           [nl.inl.blacklab.queryParser.corpusql CorpusQueryLanguageParser]
           [nl.inl.util XmlUtil]
           [org.apache.lucene.document Document]
           [org.apache.lucene.index IndexableField]))

(set! *warn-on-reflection* true)

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
         ^Hits -hits (.find searcher query)]
     -hits)))

(defn- make-hit-map
  "Base handler that takes a Hit and gives a clojure data struct"
  [^Hit -hit ^Hits -hits]
  (let [kwic (.getKwic -hits -hit)
        props (map keyword (.getProperties kwic))
        tokens (.getTokens kwic)
        hit (mapv (partial zipmap props) (partition (count props) tokens))]
    {:-hit -hit :-hits -hits :hit hit}))

(defn- -hits->window [^Hits -hits from to context]
  (.setContextSize -hits context)
  (.window -hits from to))

(defn- ^Hits get-hits
  ([searcher]
   (get-hits searcher "default"))
  ([searcher query-id]
   (let [{-hits :-hits} searcher]
     (get @-hits query-id))))

;;; middleware
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

(defn hits-handler [hits searcher]
  (let [middleware (-> wrap-clean wrap-match (wrap-doc searcher))
        handler (fn [hit] (middleware hit))]
    (for [^Hit hit hits
          :let [hit-map (make-hit-map hit hits)]]
      (handler hit-map))))

(defprotocol SearcherState
  (update-hits! [searcher query-id -new-hits]))

(defrecord BLSearcher [searchers -hits hits-handler]
  component/Lifecycle
  (start [component] component)
  (stop [component] component)
  SearcherState
  (update-hits! [seacher query-id -new-hits]
    (swap! -hits assoc query-id -new-hits)))

(defn query-size
  ([searcher corpus]
   (query-size searcher corpus "default"))
  ([searcher corpus query-id]
   (let [-hits (get-hits searcher query-id)]
     (.size ^Hits -hits))))

(defn query
  ([searcher corpus query-str from to context]
   (query searcher corpus query-str from to context "default"))
  ([searcher corpus query-str from to context query-id]
   (let [{{blsearcher corpus} :searchers
           hits-handler :hits-handler} searcher
         -hits (run-query blsearcher query-str)]
     (update-hits! searcher query-id -hits)
     (hits-handler (-hits->window -hits from to context) blsearcher))))

(defn query-range
  ([searcher corpus from to context]
   (query-range searcher corpus from to context "default"))
  ([searcher corpus from to context query-id]
   (let [{{blsearcher corpus} :searchers
          hits-handler :hits-handler} searcher
         -hits (get-hits searcher query-id)]
     (hits-handler (-hits->window -hits from to context) blsearcher))))

(defn ^HitProperty make-property
  [^Hits -hits & {:keys [criterion ^String prop-name]
                  :or {criterion :match
                       prop-name "word"}}]
  (case criterion
    :match         (HitPropertyHitText. -hits "contents" prop-name)
    :left-context  (HitPropertyLeftContext. -hits "contents" prop-name)
    :right-context (HitPropertyRightContext. -hits "contents" prop-name)))

(defn sorted-range
  ([searcher corpus from to context criterion prop-name]
   (sorted-range searcher corpus from to context criterion prop-name "default"))
  ([searcher corpus from to context criterion prop-name query-id]
   (let [{{blsearcher corpus} :searchers
          hits-handler :hits-handler} searcher
         -hits (get-hits searcher query-id)
         -hits-window (-hits->window -hits from to context)
         contents-field (:contentsFieldMainPropName blsearcher)
         hit-property (make-property -hits-window :criterion criterion :prop-name prop-name)]
     (.sort ^Hits -hits-window hit-property)
     (hits-handler -hits-window blsearcher))))

(defn new-blsearcher [paths-map]
  (let [searchers (zipmap (keys paths-map) (map make-searcher (vals paths-map)))]
    (map->BLSearcher
     {:-hits (atom {})
      :searchers searchers
      :hits-handler hits-handler})))

;;; test

(defn raw-text [hits & {:keys [n window? pprint?]
                        :or   {n 10 window? false pprint? true}}]
  (let [tokens (if window?
                 (map (partial map :word) (map :hit (take n hits)))
                 (map :word (filter :match (mapcat :hit (take n hits)))))]
    (if pprint?
      (clojure.pprint/pprint (map #(apply str (interleave (repeat " ") %)) tokens))
      tokens)))

(def paths-map {"brown-id" "/home/enrique/code/BlackLab/brown-index-id/"})

;(def searcher (new-blsearcher paths-map))
;(def hits (query searcher "brown-id" "\"a\"" 0 10 5))
;(query-range searcher "brown-id" 0 50 5)
;(query-size searcher "brown")

;; (def n 10000)
;; (def hits (query searcher "brown-id" "\".*\"" 0 n 5))
;; (clojure.pprint/pprint
;;  (map #(apply str (interleave (repeat " ") %))
;;       (raw-text (sorted-range searcher "brown-id" 0 n 2 :left-context "word")
;;                 :window? true :n n)))
;; (clojure.pprint/pprint
;;  (map #(apply str (interleave (repeat " ") %))
;;       (raw-text (query-range searcher "brown-id" 0 n 2 :left-context "word")
;;                 :window? true :n n)))
