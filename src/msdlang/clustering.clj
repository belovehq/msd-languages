(ns msdlang.clustering
  (:require
    [clojure.java.jdbc :as j]
    [clojure.java.io :refer [reader writer file resource as-file]]
    [cld.core :as cld]
    [hugsql.core :as h])
  (:import [com.mathworks.engine MatlabEngine]))


; initialisation of language detection

(cld/default-init!)


; parameters

(def paths {:params "clustering"
            :data   "clustering/data"
            :matlab "clustering/matlab"})

(def db {:classname        "org.h2.Driver"
         :subprotocol      "h2"
         :subname          "~/data/millionsong/dataset/h2/msd"
         :user             ""
         :password         ""
         :AUTO_SERVER      "TRUE"
         :AUTO_SERVER_PORT "9091"
         :DB_CLOSE_DELAY   "-1"})

(def languages
  {:af "Afrikaans", :ar "Arabic", :bg "Bulgarian", :bn "Bengali", :cs "Czech",
   :da "Danish", :de "German", :el "Greek", :en "English", :es "Spanish",
   :et "Estonian", :fa "Persian", :fi "Finnish", :fr "French", :gu "Gujarati",
   :he "Hebrew", :hi "Hindi", :hr "Croatian", :hu "Hungarian", :id "Indonesian",
   :it "Italian", :ja "Japanese", :kn "Kannada", :ko "Korean", :lt "Lithuanian",
   :lv "Latvian", :mk "Macedonian", :ml "Malayalam", :mr "Marathi", :ne "Nepali",
   :nl "Dutch", :no "Norwegian", :pa "Punjabi", :pl "Polish", :pt "Portuguese",
   :ro "Romanian", :ru "Russian", :sk "Slovak", :sl "Slovene", :so "Somali",
   :sq "Albanian", :sv "Swedish", :sw "Swahili", :ta "Tamil", :te "Telugu",
   :th "Thai", :tl "Tagalog", :tr "Turkish", :uk "Ukrainian", :ur "Urdu",
   :vi "Vietnamese", :zh-cn "Simplified Chinese", :zh-tw "Traditional Chinese"})


; database queries

(h/def-db-fns "clustering/sql/h2.sql")

(defn query-words [conn]
  (rest (h2-select-words conn {} {} {:as-arrays? true})))

(defn query-words-stems [conn]
  (h2-select-words-stems conn))

(defn query-cowords [conn wordid]
  (rest (h2-select-cowords conn {:wordid wordid} {} {:as-arrays? true})))

(defn query-artists [conn top wordids]
  (rest (h2-select-artists conn {:top top :wordids wordids} {} {:as-arrays? true})))


; utility functions

(defn fullname
  "returns the fullname of a file based on path key and file name
   e.g. (fullname :matlab) or (fullname :matlab \"test.txt\""
  ([k]
   (->> k
        (get paths)
        resource
        as-file
        str))
  ([k f] (str (fullname k) (java.io.File/separator) f)))

(defn ms-to-hms
  "converts a number of milliseconds to hh:mm:ss format"
  [ms]
  (.format
    (.plusSeconds java.time.LocalTime/MIN (/ ms 1000.0))
    java.time.format.DateTimeFormatter/ISO_LOCAL_TIME))

(defn update-map
  "apply function f to all values of map m"
  [f m]
  (reduce-kv (fn [mm k v] (assoc mm k (f v))) {} m))

(defn str-to-double [x]
  "convert a string to double"
  (Double/parseDouble x))


; cluster analysis

(defn get-cowords
  "Return the list of words and their co-occuring words.
   requery: if true then query the database and cache the result into cowords.edn (38 minutes).
   if false then just read the data from cowords.edn."
  ([]
   (get-cowords false))
  ([requery]
   (let [f (fullname :data "cowords.edn")]
     (if requery
       (with-open [out (clojure.java.io/writer f :encoding "UTF-8")]
         (.write out "[")
         (j/with-db-connection
           [conn db]
           (let [words (apply assoc (sorted-map) (flatten (query-words conn)))
                 get-cw (fn [wordid] [wordid
                                      (get words wordid)
                                      (vec (query-cowords conn wordid))])]
             (dorun (map #(.write out (str (get-cw %) \newline))
                         (keys words)))
             (.write out "]")))))
     (read-string (slurp f :encoding "UTF-8")))))


(defn coword-matrix
  "converts the list of words/cowords to a dense double[][]"
  [cowords]
  (let [zeros (vec (repeat (count cowords) 0))
        matrix-row (fn [[_ _ cw]]
                     (reduce
                       #(assoc %1 (dec (first %2)) (second %2))
                       zeros
                       cw))]
    (into-array (map (comp double-array matrix-row) cowords))))


(defn matlab-write-files
  "writes the list of words and the matrix of words/cowords to files for use in matlab."
  []
  (let [cowords (get-cowords)
        write-matrix (fn [f m]
                       (spit f (->> m
                                    (map #(apply str (interpose \space %)))
                                    (interpose \newline)
                                    (apply str))
                             :encoding "UTF-8"))]
    (write-matrix (fullname :matlab "cowords.txt") (coword-matrix cowords))
    (write-matrix (fullname :matlab "words.txt") (map (partial take 2) cowords))))


(defn matlab-cluster
  "perform a cluster analysis of the words/cowords matrix in Matlab.
   cowords: list of words/cowords
   max-clusters: max number of clusters to return data for. Word groupings will be returned for 2 ... max-clusters clusters
   average: the average function to use in Matlab, e.g. 'average'
   distance: the distance function to use in Matlab, e.g. 'cosine'
   params: alternatively, a map of the above parameters {:max-clusters 30 :average \"average\" :distance \"cosine\"}"
  ([cowords params]
   (matlab-cluster cowords (:max-clusters params) (:average params) (:distance params)))
  ([cowords max-clusters average distance]
   (let [engine (MatlabEngine/startMatlab)]
     (try
       (do
         (.eval engine (str "cd " (fullname :matlab)))
         (.eval engine "set(0,'DefaultFigureVisible','off');")
         (.feval engine "clusterwords"
                 (to-array [(coword-matrix cowords)
                            (double max-clusters)
                            average
                            distance])))
       (catch Exception e (println e))
       (finally (.close engine))))))


(defn detect-language
  "Detect the language of a list of words. The detection is performed several times on random phrases, drawn
  in a way that maximises the coverage of the list. The result is the average of the detections over all the phrases.
   phrase-size: the size of the phrases to perform the detection upon.
   phrase-count: how many phrases to perform the detection upon.
   words: the list of words"
  ([params words]
   (detect-language (:phrase-size params) (:phrase-count params) words))
  ([phrase-size phrase-count words]
   (let [; infinite seq of random phrases of given size
         phrases (mapcat #(partition (min phrase-size (count words)) (shuffle %)) (repeat words))

         ; language-detection phrase by phrase
         phrases-lang (map #(->> %
                                 (interpose \space)
                                 (apply str)
                                 cld/detect
                                 second
                                 (update-map str-to-double))
                           (take phrase-count phrases))]

     ; average the language detections across all phrases and sort from most likely to least likely
     (->> phrases-lang
          (apply merge-with +)
          (reduce-kv (fn [m k v] (assoc m (keyword k) (/ v phrase-count))) {})
          (sort-by (comp - second))))))


(defn language-stats [clusters low]
  "Language stats for one clustering.
   Returns a map with:
   :wavg : weighted average or language probabilities;
   :avg : mean of language probabilities over clusters that have more than one word;
   :=1 : count of clusters that have only one word in them."
  (let [obs (fn [{c :count prop :proportion {proba :proba} :language}]
              {:clusters 1
               :wavg     (* prop proba)
               :avg      (if (> c 1) proba 0)
               :=1       (if (> c 1) 0 1)
               :dupe     0})
        stats (->> clusters
                   (map obs)
                   (apply merge-with +))
        langs>1 (->> clusters
                     (filter #(> (:count %) 1))
                     (map #(get-in % [:language :id])))]
    (-> stats
        (update :avg / (- (double (count clusters)) (:=1 stats)))
        (assoc :dupe (- (count langs>1) (count (distinct langs>1)))))))


(defn get-artists
  "return a sample list of artists who use words in  a cluster.
  dbconn: a database connection or spec
  cluster: cluster data, see code of function analyze-clustering further down
  artist-count: how many artists to return
  word-count: how many words to take in the cluster to perform artist detection"
  ([dbconn params cluster]
   (if params
     (get-artists dbconn (:artist-count params) (:word-count params) cluster)
     []))
  ([dbconn artist-count word-count cluster]
   (flatten (query-artists dbconn artist-count (take word-count (:wordids cluster))))))


(defn compactness [wordids cowords params]
  "Calculate the compactness of a cluster as the cosine between the cluster itself and the
   words engendered by the cluster"
  (let [wids (set wordids)
        c (double (count wids))
        generated (->> cowords
                       (filter (comp wids first))
                       shuffle
                       (take (:limit params))
                       (map #(apply hash-map (flatten (last %))))
                       (reduce #(merge-with + %1 %2))
                       (update-map #(/ % c))
                       (into (sorted-map)))
        generator (select-keys generated wids)
        n2 (fn [m] (->> m
                        vals
                        (reduce #(+ %1 (* %2 %2)) 0)
                        double))]
    ;by construction here: cosine = |generator| / |generated|
    {:generated generated
     :cosine    (Math/sqrt (/ (n2 generator) (n2 generated)))}))


(defn analyze-clustering
  [cowords wordsmap lang-params wholeness-params matlab-clustering]
  " Analyze a clustering produced by matlab.
  wordsmap: the map of wordids to full words
  lang-params: map of parameters for the language recognition, see function detect-language
  artist-params: map of parameters for the artist report, see function get-artists
  matlab-clustering: the vector of clusterids returned by matlab."
  (let [;converts a Matlab clustering to a list of maps
        base-clusters (->> matlab-clustering
                           (map int)
                           (interleave (rest (range)))
                           (partition 2)
                           (group-by second)
                           (map (fn [[k v]] (assoc {} :clusterid k :wordids (sort (map first v)))))
                           (sort-by :clusterid))

        ;analyze a single cluster
        analyze-cluster (fn [cluster]
                          (let [words (map wordsmap (:wordids cluster))
                                [[langid langpr] & _ :as langs] (detect-language lang-params words)
                                {:keys [cosine generated]} (compactness (:wordids cluster) cowords wholeness-params)]
                            (assoc cluster
                              :words words
                              :count (count words)
                              :proportion (/ (double (count words)) (count matlab-clustering))
                              :language {:id langid :proba langpr :detect langs}
                              :generated generated
                              :compactness cosine)))

        ;analyse all clusters
        clusters (map analyze-cluster base-clusters)

        ; print cluster count
        _ (println (count clusters) "clusters")]

    {:count          (count clusters)
     :matlab         (map int matlab-clustering)
     :clusters       (vec clusters)
     :language-stats (language-stats clusters (:low lang-params))}))



(defn get-all-artists [artists-param clusterings]
  (if artists-param

    (let [cluster-artists (fn [dbconn c] (assoc c :artists (get-artists dbconn artists-param c)))
          do-clusters (fn [dbconn clusters] (vec (map (partial cluster-artists dbconn) clusters)))]
      (j/with-db-connection
        [conn db]
        (->> clusterings
             (map #(update % :clusters (partial do-clusters conn)))
             vec
             doall)))

    clusterings))

; Run a whole analysis

(defn run
  ([params] (run params (:requery params)))

  ([params requery]
   (let [t0 (System/currentTimeMillis)

         ; retrieve the cowords matrix
         cowords (get-cowords requery)
         words-map (apply array-map (flatten (map (partial take 2) cowords)))

         ; run the matlab clustering
         {:keys [max-clusters average distance]} (:matlab params)
         matlab-clusterings (matlab-cluster cowords max-clusters average distance)

         ; analyse the matlab clustering
         clusterings (->> matlab-clusterings
                          (pmap (partial analyze-clustering
                                         cowords
                                         words-map
                                         (:language params)
                                         ;(:artists params)
                                         (:wholeness params)))
                          doall
                          (get-all-artists (:artists params)))

         ; retrieve word stems as map indexed on wordid
         stems (reduce #(assoc %1 (:wordid %2) (dissoc %2 :wordid))
                       (sorted-map)
                       (h2-select-words-stems db))

         ; output results
         out {:params      params
              :languages   languages
              :words       stems
              :clusterings clusterings}

         _ (spit (fullname :data (:out params)) out :encoding "UTF-8")
         _ (println "Run time:" (ms-to-hms (- (System/currentTimeMillis) t0)))]

     out)))


; run with Leiningen

(defn -main
  "Runs the default cluster analysis, as per resources/clustering/default.edn"
  [& args]
  (->> "default.edn"
       (fullname :params)
       slurp
       read-string
       run
       dorun))


; REPL candy

(comment
  (def out (run {:requery   false
                 :matlab    {:max-clusters 30 :average "average" :distance "cosine"}
                 :language  {:phrase-size 50 :phrase-count 1000}
                 :wholeness {:limit 200}
                 :artists   {:artist-count 10 :word-count 20}
                 :out       "out.edn"})))


