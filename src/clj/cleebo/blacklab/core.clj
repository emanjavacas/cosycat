(ns cleebo.blacklab.core
  (:require [taoensso.timbre :as timbre])
  (:import [nl.inl.blacklab.search Searcher
            Hit Hits Concordance Kwic TextPatternRegex]
           [nl.inl.blacklab.search.grouping
            HitProperty HitPropertyHitText HitPropertyLeftContext HitPropertyRightContext]
           [nl.inl.blacklab.queryParser.corpusql CorpusQueryLanguageParser]
           [nl.inl.util XmlUtil]
           [org.apache.lucene.document Document]
           [org.apache.lucene.index IndexableField]))

(set! *warn-on-reflection* true)

(defn update-range
  "Updates v applying function f to the items at the positions
  by a range. See `range` for its function signature"
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

(defn make-searcher 
  "Creates a searcher given index path. It does not close the searcher"
  ^Searcher  [^String path]
  (Searcher/open (java.io.File. path)))

(defn destroy-searcher
  [^Searcher searcher]
  (.close searcher))

(defn run-query 
  "Runs the query and returns the general Hits object"
  ^Hits 
  ([^Searcher searcher ^String s]
   (run-query searcher s unmod-query))
  ([^Searcher searcher ^String s query-handler]
   (let [^TextPatternRegex query (query-handler s)
         ^Hits -hits (.find searcher query)]
     -hits)))

(defn- make-hit-map
  "Base handler that takes a Hit and gives a clojure data struct.
  We need to keep -hits around because KWIC relies on it"
  [^Hit -hit ^Hits -hits]
  (let [kwic (.getKwic -hits -hit)
        props (map keyword (.getProperties kwic))
        tokens (.getTokens kwic)
        hit (mapv (partial zipmap props) (partition (count props) tokens))]
    {:-hit -hit :-hits -hits :hit hit}))

(defn- -hits->window [^Hits -hits from to context]
  (let [size (- to from)]
    (.setContextSize -hits context)
    (.window -hits from size)))

;;; middleware
(defn wrap-clean
  "Basic handler that removes the -hit key from hit-map"
;  [handler]
  [hit-map] (dissoc hit-map :-hit :-hits))

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

(defn wrap-hit-id
  "Add hit id"
  [handler]
  (fn [{:keys [-hit hit] :as hit-map}]
    (let [new-map (assoc hit-map :id (.hashCode -hit))]
      (handler new-map))))

(defn hits-handler [hits searcher]
  (let [middleware (-> wrap-clean wrap-match wrap-hit-id (wrap-doc searcher))
        handler (fn [hit] (middleware hit))]
    (for [^Hit hit hits
          :let [hit-map (make-hit-map hit hits)]]
      (handler hit-map))))

(defn ^HitProperty make-property
  "creates a property for sorting purposes"
  [^Hits -hits criterion ^String prop-name]
  (case criterion
    :match         (HitPropertyHitText. -hits "contents" prop-name)
    :left-context  (HitPropertyLeftContext. -hits "contents" prop-name)
    :right-context (HitPropertyRightContext. -hits "contents" prop-name)))

(defn query-size
  "sorts a given Hits object"
  [-hits]
  (.size ^Hits -hits))

(defn query
  "runs a query and returns a window of hits specified by `from` `to` and `context`.
  Accepts an optional function that will be called for side effects with optional args
  and the resulting hits"
  ([searcher hits-handler query-str from to context]
   (query searcher hits-handler query-str from to context identity))
  ([searcher hits-handler query-str from to context f & args]
   (let [-hits (run-query searcher query-str)]
     (apply f (concat args [-hits]))
     (hits-handler (-hits->window -hits from to context) searcher))))

(defn query-range
  "returns a window of hits specified by `from` `to` and `context` from the result of
  a previous query."
  [searcher -hits hits-handler from to context]
  (hits-handler (-hits->window -hits from to context) searcher))

(defn sort-query
  "returns a specified hits window after sorting the entire query results."
  [searcher -hits hits-handler from to context criterion prop-name]
  (let [hit-property (make-property -hits criterion prop-name)]
    (.sort ^Hits -hits hit-property)
    (hits-handler (-hits->window -hits from to context) searcher)))

(defn sort-range
  "sorts a specified hits window."
  [searcher -hits hits-handler from to context criterion prop-name]
  (let [-hits-window (-hits->window -hits from to context)
        hit-property (make-property -hits-window criterion prop-name)]
    (.sort ^Hits -hits-window hit-property)
    (hits-handler -hits-window searcher)))

(defn snippet ;todo home-made function to handle xml-conc; add anns to it?
  [^Hits -hits hit-idx snippet-size]
  (let [^Hit -hit (.get -hits hit-idx)
        ^Concordance conc (.getConcordance -hits -hit snippet-size)]
    (->> [(.left conc) (.match conc) (.right conc)]
         (map #(XmlUtil/xmlToPlainText %))
         (zipmap [:left :match :right]))))

;;; test
;; (defn raw-text
;;   [hits & {:keys [n window? pprint?] :or {n 10 window? false pprint? true}}]
;;   (let [tokens (if window?
;;                  (map (partial map :word) (map :hit (take n hits)))
;;                  (map :word (filter :match (mapcat :hit (take n hits)))))]
;;     (if pprint?
;;       (clojure.pprint/pprint (map #(apply str (interleave (repeat " ") %)) tokens))
;;       tokens)))

;; (defn print-ids [hits & {:keys [print?] :or {print? false}}]
;;   (let [f (if print? prn identity)]
;;     (f (map :id (filter :match (mapcat :hit hits))))))

;; (defn check-equals [s1 s2]
;;   (= (into (hash-set) s1) (into (hash-set) s2)))

;; (defn check-overlap [s1 s2]
;;   (clojure.set/difference (into (hash-set) s1) (into (hash-set) s2)))

;; (def paths-map {"brown-id" "/home/enrique/code/BlackLab/brown-index-id/"})

;; (def searcher (make-searcher (get paths-map "brown-id")))

;; (def -hits (run-query searcher "\"a\""))

;; (for [i (range 1 10)
;;       :let [from (* i 100)
;;             to (+ from 10)
;;             hits (query searcher "brown-id" "\"a\"" from to 10 5)
;;             sorted-hits (sort-range searcher "brown-id" from to 5 :left-context "word")]]
;;   [(check-equals (print-ids hits) (print-ids sorted-hits))
;;    (count (check-overlap (print-ids hits) (print-ids sorted-hits)))])

;; (def hits (query searcher "brown-id" "\"a\"" 0 20 5))
;; (def sorted-hits (sort-range searcher "brown-id" 0 10 5 :left-context "word"))

;; (query-range searcher "brown-id" 0 50 5)
;; (query-size searcher "brown")

;; (def n 10000)
;; (def hits (query searcher "brown-id" "\".*\"" 0 n 5))
;; (clojure.pprint/pprint
;;  (map #(apply str (interleave (repeat " ") %))
;;       (raw-text (sort-range searcher "brown-id" 0 n 2 :left-context "word")
;;                 :window? true :n n)))
;; (clojure.pprint/pprint
;;  (map #(apply str (interleave (repeat " ") %))
;;       (raw-text (query-range searcher "brown-id" 0 n 2 :left-context "word")
;;                 :window? true :n n)))
