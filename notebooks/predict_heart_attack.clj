(ns predict-heart-attack

  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [tablecloth.api :as tc]
            [tech.v3.dataset :as tds]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.hashing :as h]
            [nextjournal.clerk.webserver :as webserver]
            [weavejester.dependency :as dep]
            [clojure.string :as str]
            [opencpu-clj.ocpu :as ocpu]
            [clojure.java.io :as io]
            [pps :as pps]))

(comment



  (reset! webserver/!doc {})
  (clerk/clear-cache!)


  (clerk/halt!)
  (clerk/serve! {:browse? true})
  (clerk/serve! {:watch-paths [""]}))

(clerk/set-viewers! [{:pred tc/dataset?
                      :transform-fn #(clerk/table {:head (tc/column-names %)
                                                   :rows (tc/rows  % :as-seq)})}])
(def col-names
  [:age
   :sex
   :cp
   :trestbps
   :chol
   :fbs
   :restecg
   :thalach
   :exang
   :oldpeak
   :slope
   :ca
   :thal
   :target])

(def data

  (->> "data/cleve.mod"
       slurp
       str/split-lines
       (map #(str/split % #"\s{1,}"))
       (map #(into-array String %))
       (tech.v3.dataset.io.string-row-parser/rows->dataset {:header-row? false})))

(def ds
  (-> data
      (tc/rename-columns (zipmap (map #(str "column-" %) (range))
                                 col-names))
      (tc/drop-columns ["column-14"])))



(def pps-matrix
  (pps/calc-pps-matrix ds))

(def pps-scores
  (->> pps-matrix
       :scores
       (map #(update % :pps (fn [pps]
                              (Float/parseFloat  (format "%.2f" (float pps))))))))


(clerk/vl

 {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
  :usermeta {:embedOptions {:renderer "svg"}}
  :config {:axis {:grid true :tickBand "extent"}}
  :width 600
  :height 600
  :data {:values (vec pps-scores)}
  :encoding {:x {:field "x" :type "ordinal"}
             :y {:field "y" :type "ordinal"}}
  :layer [{:encoding {:color {:field "pps"
                              :legend {:orient "top"
                                       :direction "horizontal"
                                       :gradientLength 120}
                              :title "PPS"
                              :type "quantitative"}}
           :mark "rect"}
          {:encoding {
                      :text {:field "pps" :type "quantitative"}}
           :mark "text"}]})
