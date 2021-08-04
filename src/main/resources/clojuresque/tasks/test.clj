(ns clojuresque.tasks.test
  (:use
    [clojure.test :only (run-tests) :as t]))

(alias 'test-junit 'clojuresque.tasks.test-junit)
(refer 'clojuresque.util :only '[deftask namespaces])

(defn check-result
  [{:keys [fail error], :as _result}]
  (and (= 0 fail)
       (= 0 error)))

(defn test-namespaces
  [{:keys [source-files]}]
  (let [namespaces (namespaces source-files)]
    (apply require namespaces)
    (check-result (apply run-tests namespaces))))

; For now: do stuff manually for explicitly named tests.
(defn test-vars
  [{:keys [tests]}]
  (let [tests (->>
              (clojure.string/split tests #",")           ; split comma delimited list
              (map read-string)                           ; read each value as a symbol
              (filter namespace)                          ; drop java class names
              (group-by (comp symbol namespace)))]        ; group by namespace
    (apply require (keys tests))
    (binding [t/*report-counters* (ref t/*initial-report-counters*)]
      (doseq [[nspace test-vars] tests]
        (let [ns-obj          (the-ns nspace)
              once-fixture-fn (t/join-fixtures
                                (::t/once-fixtures (meta ns-obj)))
              each-fixture-fn (t/join-fixtures
                                (::t/each-fixtures (meta ns-obj)))]
          (t/do-report {:type :begin-test-ns :ns ns-obj})
          (once-fixture-fn
            (fn []
              (doseq [v (map resolve test-vars)]
                (when (:test (meta v))
                  (each-fixture-fn (fn [] (t/test-var v)))))))
          (t/do-report {:type :end-test-ns :ns ns-obj})))
      (let [summary (assoc @t/*report-counters* :type :summary)]
        (t/do-report summary)
        (check-result summary)))))

(deftask main
  [{:keys [junit tests] :as options}]
  (let [exit-code (atom 0)]
    (try
      (let [success? (cond
                       junit (test-junit/test-namespaces options)
                       (seq tests) (test-vars options)
                       :else (test-namespaces options))]
        (when-not success?
          (reset! exit-code -1))
        success?)
      (catch Throwable t
        (println "Exception thrown while running tests: " t)
        (reset! exit-code -2))
      ;; Some stray non-daemon threads may cause the JVM to hang on exit.
      ;; Call this after all tests have run to allow proper shutdown.
      (finally
        (shutdown-agents)
        (.awaitTermination (clojure.lang.Agent/soloExecutor) 0 java.util.concurrent.TimeUnit/MILLISECONDS)
        (.awaitTermination (clojure.lang.Agent/pooledExecutor) 0 java.util.concurrent.TimeUnit/MILLISECONDS)
        (System/exit @exit-code)))))
