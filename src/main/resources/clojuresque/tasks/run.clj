(ns clojuresque.tasks.run
  (:require [clojuresque.util :refer [deftask]]))

(deftask main
  [{:keys [fn]}]
  (let [[fn-string & args] (clojure.string/split fn #"\s+")
        fn (symbol fn-string)]
    (require (symbol (namespace fn)))
    (apply (resolve fn) args)
    true))
