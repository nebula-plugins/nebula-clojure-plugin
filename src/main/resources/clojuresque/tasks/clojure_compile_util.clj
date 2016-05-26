(ns clojuresque.tasks.clojure-compile-util
  (:require
   [clojure.tools.namespace.dependency :as deps]
   [clojure.tools.namespace.file       :as file]
   [clojure.tools.namespace.track      :as track]))

(defn invert-map
  [m]
  (reduce-kv #(assoc %1 %3 %2) {} m))

(defn file-dependencies
  [files]
  (let [tracker (file/add-files (track/tracker) files)
        graph   (::track/deps tracker)
        nodes   (deps/nodes graph)
        fmap    (invert-map (::file/filemap tracker))]
    (->> nodes
      (map #(set (map fmap (deps/immediate-dependents graph %))))
      (zipmap (map fmap nodes)))))
