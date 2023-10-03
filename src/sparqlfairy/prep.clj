(ns sparqlfairy.prep
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [clj-http.client :as client]))


(def path "data/prompted/gpt")
(def files (map str (filter #(.isFile %) (file-seq (io/file path)))))

(defn read-chats
  "Read series of ChatGPT chat completions in a directory or archive"
  [path]
  (mapv (fn [f] (read-string (slurp f))) files))

(defn model-stats
  "Quickly profile chat models used"
  [chats]
  (frequencies (map :model chats)))

(defn seq-quest
  "Extract question from response; even ChatGPT-4 can be a little inconsistent and sometimes includes #'s"
  [string] (re-seq #"(?<=Question.*: ?\n?).+" string))

(defn seq-query [string]
  (->>(re-seq #"(?<=\:)\s?\n?\s?`[^`]*" string)
      ;(re-seq #"(?<=Query.*:.*\n?.*)`[^`]*" string)
      (map #(str/replace % "`" ""))))

(defn parse-msg
  "Parse ChatGPT completion response to get question-query data"
  [msg]
  (let [content (get-in (first (msg :choices)) [:message :content])]
    { :id (msg :id) :question (seq-quest content) :query (seq-query content)}))

(def data (read-chats "data/prompted/gpt"))

(def dataset (map parse-msg data))

; Check and QC examples generated
(def nx (map (fn [x] {:id (x :id)
                      :nquest (count (x :question))
                      :nquery (count (x :query)) }) dataset))

(def dataset-nx (set/join dataset nx))

;; sum total questions generated
(reduce + (map :nquest nx))

;; get endpoint from env
;; (def endpoint (System/getenv "SPARQL_ENDPOINT"))
(def endpoint "http://npoddatagraph.westus.azurecontainer.io:7200/repositories/nPOD-dev?")

(defn qc-query
  "QC query by running it against the graph SPARQL endpoint;
  add default prefix into query"
  [query endpoint]
  (let [prefix "PREFIX : <http://purl.org/net/obi-wan#> "
        prefixed-query (str prefix query)]
    (client/get (str endpoint (client/generate-query-string {"query" prefixed-query})) {:as :byte-array})))

(defn row-map [chat]
  (mapv (fn [quest query]
          {:instruction quest
           :input ""
           :output query}) (chat :question) (chat :query)))

;(filter #(not= (% :nquery) (% :nquest)) dataset)

(def all-rows (mapcat row-map dataset))


(def filtered (filter #(not (nil? (:output %))) all-rows))

(spit "data.json" (json/write-str filtered))
