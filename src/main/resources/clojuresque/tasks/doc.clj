(ns clojuresque.tasks.doc)

(refer 'clojuresque.util :only '[deftask])
(refer 'clojuresque.codox.main :only '[generate-docs])

(deftask main
  [{:keys [project codox destination-dir source-dirs source-files]}]
  (let [project-map (merge project
                           {:output-dir  destination-dir
                            :sources     source-files
                            :source-dirs source-dirs}
                           codox)]
    (generate-docs project-map)
    true))
