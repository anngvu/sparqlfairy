(ns sparqlfairy.prompt
  (:require [tolkien.core :as token]
            [wkok.openai-clojure.api :as api]
            [nano-id.core :as id :refer [nano-id]]))

(def sys-persona
  "You are a bioinformatician who is knowledgeable, helpful, and expert at SPARQL. Your job is to help others understand and make use of the nPOD diabetes knowledge graph that contains info about tissue donors, data, and other scientific resources.")

;; Ontologies/data models of interest
(def ow-full "https://gitlab.com/npod/obi-wan/-/raw/master/obi-wan.ttl")
(def ow-data "https://gitlab.com/npod/obi-wan/-/raw/develop/shorts/data.ttl")
(def ow-ops "https://gitlab.com/npod/obi-wan/-/raw/develop/shorts/data_ops_context.ttl")
(def ow-bio "https://gitlab.com/npod/obi-wan/-/raw/develop/shorts/data_bio_context.ttl")
(def htan "https://raw.githubusercontent.com/ncihtan/data-models/main/HTAN.model.jsonld")

;; https://help.openai.com/en/articles/6654000-best-practices-for-prompt-engineering-with-openai-api
(defn compose [src n]
  (str "Given the ontology below describing entities and relationships in the graph, provide " n " example natural language questions to ask and the corresponding SPARQL query."
       "Desired format: \n"
       "Question: <question> \n"
       "Query: `<query>` \n"
       "Ontology: ###"
       (slurp src)))

(defn chat-context
  "Set up default chat context"
  [model content]
  {:model model
   :messages
   [{:role "system" :content sys-persona }
    {:role "user" :content content}]})

(defn count-tokens
  "Handle some accounting"
  [model content]
  (token/count-chat-completion-tokens (chat-context model content)))

(defn chat-prompt
  [model content]
  (api/create-chat-completion (chat-context model content)))

(def content-htan (compose htan 100))

(def content-full (compose ow-data 100))
(count-tokens "gpt-3.5-turbo-16k" content-full)

(def content-short-ops (compose ow-ops 100))
(count-tokens "gpt-4" content-short-ops)

(def content-short-bio (compose ow-bio 100))
(count-tokens "gpt-4" content-short-bio)

;; TODO check that content size suitable for specified model
(defn prompt! [model content times]
  (dotimes [n times]
    (println "Iteration index:" n)
    (let [results (chat-prompt model content)]
      (spit (str "data/prompted/gpt/" (nano-id 8) ".clj") (pr-str results)))))

(prompt! "gpt-4" content-short-ops 1)
;(prompt! "gpt-4" content-short-bio 5)
;(prompt! "gpt-3.5-turbo-16k" content-full 5)
