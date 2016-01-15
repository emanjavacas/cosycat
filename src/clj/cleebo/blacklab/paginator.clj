(ns cleebo.blacklab.paginator
    (:import [nl.inl.blacklab.search Hits HitsWindow]))

(set! *warn-on-reflection* true)

(defn pager-next
  ([size page-size] (pager-next size page-size 0))
  ([size page-size from]
   (let [to (+ from page-size)]
     (if (>= to size) 
       [from 0]
       [from to]))))

(defn pager-prev
  ([size page-size] (pager-prev size page-size 0))
  ([size page-size from]
   (let [new-from (- from page-size)]
     (cond (zero? from) [(- size page-size) size]
           (zero? new-from) [0 page-size]
           (neg?  new-from)  [0 (+ new-from page-size)]
           :else [new-from from]))))

(defprotocol PaginateHits
  (get-size [this])
  (get-position [this])
  (get-window-size [this])
  (set-window-size! [this window-size])
  (^HitsWindow current-page [this page-size])
  (^HitsWindow next-page [this page-size])
  (^HitsWindow prev-page [this page-size])
  (^HitsWindow nth-page [this n page-size]))

(defrecord Paginator [hits init] ;private record
  PaginateHits
  (get-size [this] (.size ^Hits hits))  
  (get-position [this] @init)
  (get-window-size [this] (.getContextSize ^Hits hits))
  (set-window-size! [this window-size] (.setContextSize ^Hits hits window-size))
  (current-page [this page-size] (.window ^Hits hits @init page-size))
  (next-page [this page-size]
    (dosync 
     (let [[from to] (pager-next (.size ^Hits hits) page-size @init)]
       (.window ^Hits hits (ref-set init to) page-size))))
  (prev-page [this page-size]
    (dosync 
     (let [[from to] (pager-prev (.size ^Hits hits) page-size @init)]
       (.window ^Hits hits (ref-set init from) page-size))))
  (nth-page [this n page-size]
    (dosync 
     (.window ^Hits hits (ref-set init (* n page-size)) page-size))))

(defn paginator [^Hits hits] ;record constructor
  (let [current (ref 0)]
    (Paginator. hits current)))

