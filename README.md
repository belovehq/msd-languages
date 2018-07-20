# Language clustering of the MSD musicXmatch dictionary 

musiXmatch contributed the lyrics of 
[237,662 tracks](https://labrosa.ee.columbia.edu/millionsong/musixmatch) 
to the [Million Song Dataset](https://labrosa.ee.columbia.edu/millionsong/). 
These lyrics are restricted to the top 5,000 words across the set, 
which contains several languages. The dataset doesn't tell in which language 
each song is written, nor to which language(s) each word might belong. 
We do not know how many languages there are in the dataset, nor their proportions 
in the dictionary. Nevertheless, it may be useful to group words and/or songs per language. 

This project uses hierarchical clustering to group words by language, 
based on word co-occurences in the dataset. The rationale for using hierarchical 
clustering over other clustering methods is that a) we do no know how many 
clusters/languages there might be in the dataset and b) we can expect 
clusters/languages to have very different word counts. 
Hierarchical clustering places every word on a same footing and allows 
for the detection of clusters of vastly varying sizes.

## Output

The clustered dictionary of the musicXmatch dataset can be found in file 
mxm_clustered_dictionary.txt. A Jupyter notebook (part of this project and also online) 
explains how the clusters were chosen.

This dictionary is skewed towards the most represented languages in the dataset, 
e.g. the word "in" is classified as English despite belonging to many other languages.

The analysis is based on the musiXmatch dataset, the official lyrics collection 
for the Million Song Dataset, available at: http://labrosa.ee.columbia.edu/millionsong/musixmatch

## Computation

The analysis is written in Clojure.

* The code requires the musiXmatch dataset to have been already loaded into 
an H2 SQL database.
 
* A matrix of word co-occurences is then assembled, with element (i,j) 
the number of tracks in which words (i) and (j) appear together
(standardised by the total number of tracks in which word (i) appears). 
Each word is hence represented in the matrix by the vector of its 
"co-words" in the dataset. The rationale behind this is that if two 
words have the same set of co-words, then these two words are likely
 to belong to the same language.

* A hierarchical linkage of the words is performed in Matlab using 
cosine distance and unweighted average to compute the distance between clusters. 
The resulting dendrogram was sliced at different levels to produce clusterings 
with between two and thirty clusters. 

* To help decide how many clusters to consider, each cluster is submitted to 
the [language-detection](https://code.google.com/archive/p/language-detection/)
Java library. For each cluster, the detection is 
performed over a thousand random phrases of fifty words. Each cluster is 
allocated the language that seemed the most likely.

* Each cluster is also attributed a measurement of "compactness", 
calculated as the cosine of the angle between the cluster and the set 
of words engendered by the cluster. The higher this cosine, the least the 
cluster correlates with other words in the dataset. 

* Finally, the database is queried again in order to produce a sample list
 of artists for every cluster (naively: top x artists that use the top y words 
 in the cluster)

The output of this computation is an [edn](https://github.com/edn-format/edn) file. A Jupyter notebook is then used to view 
the output and assess the optimal number of clusters (also online). 

## Requirements

The analysis requires:
 
* [Leiningen](https://leiningen.org/), the build automation tool for Clojure.
 
* The [H2 database of MSD lyrics](https://github.com/belovehq/msd-lyrics-to-h2), whose path
needs to be edited in `clustering.clj`.

* [Matlab](https://github.com/belovehq/msd-lyrics-to-h2), the numerical computing environment by Mathworks. 
The Matlab Java engine API should be added to the local Leiningen repository with the `lein-localrepo` plugin.

* [Jupyter notebook](http://jupyter.org/), together with the [BeakerX](http://beakerx.com/) collection of Jupyter kernels. 

## Usage

To run the computation with Leinigen: 

* Edit the dependency to Matlab in `project.clj` to match the version of the Matlab engine 
registered in your local Leiningen repo. 

* Edit the path to the database in `clustering.clj`. 

* Then type `lein run` to run the analysis with the parameters 
stored in `resources/clustering/default.edn`. See the code of `clustering.clj` 
for the meaning of parameters.

* Alternatively, the computation can be run from the REPL, 
e.g in namespace `msdlang.clustering`:

```clojure
(def out 
  (run {:requery   true
        :matlab    {:max-clusters 30 :average "average" :distance "cosine"}
        :language  {:phrase-size 50 :phrase-count 1000}
        :wholeness {:limit 200}
        :artists   {:artist-count 10 :word-count 20}
        :out       "out.edn"}))
``` 

Whereas the cluster analysis itself only takes a short time, SQL queries in H2
take a long time (close to 1h on the machine on which this project was coded). 
This includes creating the cowords matrix (pre-analysis) and 
then requesting the artists that use words in clusters (post-analysis). To speed things up: 

* The code caches the cowords matrix in `/resources/clustering/data/cowords.edn`, 
so the cost of building the matrix only has to be paid once. Set the `:requery`
parameter to `true` to query the database a first time, 
and to `false` subsequently to use the cached file.

* The request of the top artists per cluster can be turned off by removing `:artists` 
from the parameters map. 

Once the analysis has run, launch `jupyter notebook` and open the notebook
in `resources/clustering/jupyter` to explore the results stored in 
`/resources/clustering/data/out.edn`. See the online version of the notebook.

## License


Copyright © 2018 Nicolas Duchenne, Belove Ltd, London, UK

Released under the [MIT License](https://opensource.org/licenses/MIT).

Based on the musiXmatch dataset, the official lyrics collection for the Million Song Dataset, 
             available at: http://labrosa.ee.columbia.edu/millionsong/musixmatch
