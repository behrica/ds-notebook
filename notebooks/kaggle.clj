(ns  kaggle

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
            [pps :as pps]
            [clojure.tools.analyzer.jvm :as ana])
            ;; [com.rpl.nippy-serializable-fn] ;; enable freeze of fns)
   
  (:import [javax.imageio ImageIO]))

;; Text block 21 gets analused wrongly

(->
 (clerk/parse-file "notebooks/kaggle.clj")
 :blocks
 (nth 22)
 :text
 read-string
 h/analyze
 :deps)


(comment


  (webserver/!doc)
  (reset! webserver/!doc {})
  (clerk/clear-cache!)

  (clerk/blob->result)
  (clerk/recompute!)

  (clerk/halt!)
  (clerk/serve! {:browse? true})
  (clerk/serve! {:watch-paths ["src"]}))



(clerk/set-viewers! [{:pred tc/dataset?
                      :transform-fn #(hash-map
                                      :nextjournal/value
                                      (clerk/table {:head (tds/column-names %)
                                                    :rows (tds/rowvecs %)}))}])





(defn load-hp-data [file]
  (println "load a file : " file)
  (-> (tc/dataset file {:key-fn keyword})
      ;; (tc/head 100)
      (tc/convert-types (zipmap [:BedroomAbvGr
                                 :BsmtFullBath
                                 :BsmtHalfBath
                                 :Fireplaces
                                 :FullBath
                                 :GarageCars
                                 :HalfBath
                                 :KitchenAbvGr
                                 :OverallCond
                                 :OverallQual
                                 :MoSold
                                 :TotRmsAbvGrd
                                 :MSSubClass
                                 :YrSold]
                                (repeat :string)))
      (tc/rename-columns {
                          :1stFlrSF :FirststFlrSF
                          :2ndFlrSF :SecondFlrSF
                          :3SsnPorch :ThirdsnPorch})))







;;  # The data
^{::clerk/width :full}
(def df (load-hp-data "train.csv.gz"))

^{::clerk/width :full}
(->
 df
 (tc/info))


 ;; # Info on data
;; ## data types
(-> df
    tc/info
    :datatype
    frequencies)

(-> df
    tc/info
    (tc/select-columns [:col-name :n-missing])
    (tc/order-by :n-missing :desc))
    


(def col-names-categorical
  (-> df
      tc/info
      (tc/select-rows (fn [row] (= :string (:datatype row))))
      :col-name
      seq))
      


(def col-names-int
  (-> df
      tc/info
      (tc/select-rows (fn [row] (contains?  #{:int16 :int32} (:datatype row))))
      :col-name
      seq))


(clerk/vl
 {::clerk/width :full}
 {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
  :data {:values (vec (tc/rows df :as-maps))}
  :title "Categorical cols"
  :repeat col-names-categorical
  :columns 3
  :spec {
         :width 200
         :height 200
         :encoding {:color {:field {:repeat :repeat} :legend nil :type "nominal"}
                    :x {:field {:repeat :repeat}
                        :type "nominal"
                        :axis {:titleFontSize 20}}
                    :y {:field :SalePrice :scale {:zero false} :type "quantitative"}}
         :mark {:extent "min-max" :type "boxplot"}}})



(clerk/vl
 {::clerk/width :full}
 {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
  :data {:values (vec (tc/rows df :as-maps))}
  :title "Integer cols"
  :repeat col-names-int
  :columns 3
  :spec {
         :width 200
         :height 200
         :encoding {
                    :x {:field {:repeat :repeat}
                        :type :quantitative
                        :scale {:zero false}
                        :axis {:titleFontSize 20}}
                    :y {:field :SalePrice :scale {:zero false} :type "quantitative"}}
         :mark {:extent "min-max" :type "point"}}})



;;  # PPS


(def pps (pps/calc-pps df "SalePrice"))



(clerk/vl
 {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
  :data {:values (-> pps :scores)}

  :encoding {:x {:field "pps" :title "PPS" :type "quantitative"}
             :y {:field "x" :sort "-x" :type "ordinal"}}
  :height 800
  :width 500
  :mark "bar"})



(def top-x-pps
  (->> pps
       :scores
       (sort-by :pps)
       reverse
       (drop 1)
       (take 15)
       (map (comp keyword :x))))









(def pps-top-x (pps/calc-pps-matrix (tc/select-columns df top-x-pps)))


(def pps-scores
  (->> pps-top-x
       :scores
       (map #(update % :pps (fn [pps]
                              (Float/parseFloat  (format "%.2f" (float pps))))))))




(clerk/vl

 {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
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


(require '[scicloj.ml.core :as ml]
         '[scicloj.ml.metamorph :as mm]
         '[scicloj.ml.dataset :as ds])


(def train-data (load-hp-data "train.csv.gz"))

(def splits
  (->
   train-data
   (ds/split->seq :kfold)))

(def cat-features (clojure.set/intersection
                   (into #{} col-names-categorical)
                   (into #{} top-x-pps)))

(def numeric-features (clojure.set/intersection
                       (into #{} col-names-int)
                       (into #{} top-x-pps)))


(def pipe-fn
  (ml/pipeline
   (mm/select-columns
    (concat cat-features numeric-features [:SalePrice]))
   (mm/replace-missing cat-features :value :NA)
   (mm/replace-missing numeric-features :midpoint)


   (fn [ctx]
     (assoc ctx :metamorph.ml/full-ds train-data))
   (mm/transform-one-hot cat-features  :full)

   (mm/set-inference-target :SalePrice)

   {:metamorph/id :model}
   (mm/model {:model-type :smile.regression/gradient-tree-boost
              ;; :max-depth 20
              ;; :max-nodes 10
              ;; :node-size 8
              :trees 1000})))



(defn train []
  (println "Train")
  (ml/evaluate-pipelines [pipe-fn pipe-fn] splits ml/rmse
                         :loss {:evaluation-handler-fn (fn [r] (dissoc r :pipe-fn :metric-fn))}))

(def result (train))


(def mean-loss
  (-> result first first :test-transform :mean))
(spit "loss.txt" (int  mean-loss))

(def best-pipe-fn (-> result first first :pipe-fn))

(def trained-ctx
  (pipe-fn {:metamorph/data train-data
            :metamorph/mode :fit}))

(def test-data
  (load-hp-data "test.csv.gz"))

(def df-test
  (-> test-data
   (tc/add-column :SalePrice 0)))

(def test-ctx
  (pipe-fn
   (assoc trained-ctx
          :metamorph/data df-test
          :metamorph/mode :transform)))



(def submission (->  (ds/select-columns df-test [:Id])
                     (ds/add-column :SalePrice (-> test-ctx :metamorph/data :SalePrice))))


(ds/write-csv! submission "submission.csv")



(comment
  (def times
    (doall
     (repeatedly 10 (fn [] (clerk/time-ms (clerk/show! "src/kaggle.clj"))))))

  :ok)
