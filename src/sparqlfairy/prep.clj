(ns sparqlfairy.prep
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
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
  (->>(re-seq #"(?<=Query.*:.*\n?.*)`[^`]*" string)
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

;; more stats
(reduce + (map :nquest nx))

(filter #(not= (% :nquery) (% :nquest)) dataset)
