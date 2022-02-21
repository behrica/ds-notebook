(ns pps
  (:require [opencpu-clj.ocpu :as ocpu]
            [clojure.string :as str]
            [tablecloth.api :as tc]))

(def base-url "https://my-container-app.livelybay-00debe37.westeurope.azurecontainerapps.io")
;; (def base-url "http://localhost:8080")


(defn r-object [library function params]
  ;; (prn :time (java.util.Date.) :req library function params)
  (let [resp (ocpu/object base-url :library library  :R function params)]
    ;; (prn :time  (java.util.Date.) :resp resp)
    (when (>  (:status resp) 201) (throw (ex-info "error" resp)))
    (-> resp
        :result
        first
        (str/split #"/")
        (nth 3))))

(defn r-graph [library function params]
  (-> (ocpu/object base-url :library library  :R function params)
      :result
      (nth 2)))



(defn calc-pps [df target]
  (println "calc pps")
  (tc/write-csv! df "/tmp/out.csv")
  (let [
        ;; last-run  #inst "2022-02-16T20:42:44.871-00:02"
        clean-data
        (as-> "/tmp/out.csv" _
          (r-object "readr"   "read_csv"     {:file {:file _}})
          ;; (r-object "janitor" "clean_names"  {:dat  _})
          (r-object "dplyr"   "mutate_if"    {".tbl" _
                                              ".predicate" "is.character"
                                              ".funs" "as.factor"}))
        pps-score
        (r-object "ppsr" "score_predictors"
                  {:df clean-data
                   :y (format  "'%s'" target)
                   :do_parallel true})]



    {
     :scores (:result (ocpu/session base-url (str "/ocpu/tmp/" pps-score "/R/.val" ) :json))}))

(defn calc-pps-matrix [df]
  (println "calc pps matrix")
  (-> df
    ;; (tc/select-columns candidate-features)
    (tc/write-csv! "/tmp/out.csv"))
  (let [
        last-run  #inst "2022-02-16T20:42:44.871-00:02"
        clean-data
        (as-> "/tmp/out.csv" _
          (r-object "readr"   "read_csv"     {:file {:file _}})
          (r-object "dplyr"   "mutate_if"    {".tbl" _
                                              ".predicate" "is.character"
                                              ".funs" "as.factor"}))
        pps-score
        (r-object "ppsr" "score_df"
                  {:df clean-data
                   :do_parallel true})]



    {
     :scores (:result (ocpu/session base-url (str "/ocpu/tmp/" pps-score "/R/.val" ) :json))}))
