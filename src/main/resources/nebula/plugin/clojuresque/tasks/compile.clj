(ns clojuresque.tasks.compile)

(refer 'clojuresque.util :only '[deftask namespaces])

(deftask main
  [{:keys [compile-mode warn-on-reflection source-files]}]
  (let [mode (condp = compile-mode
               "compile" clojure.core/compile
               "require" clojure.core/require
               (throw
                 (Exception. "You must choose a mode: compile or require.")))
        namespaces (namespaces source-files)]
    (binding [*warn-on-reflection* warn-on-reflection
              *compile-path*       (System/getProperty "clojure.compile.path")]
      (doseq [nspace namespaces]
        (mode nspace))))
  true)
