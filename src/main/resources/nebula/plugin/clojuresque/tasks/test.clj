(ns clojuresque.tasks.test
  (:use
    [clojure.test :only (run-tests) :as t]))

(alias 'test-junit 'clojuresque.tasks.test-junit)
(refer 'clojuresque.util :only '[deftask namespaces])

(defn check-result
  [result]
  (and (zero? (:fail result)) (zero? (:error result))))

(defn test-namespaces
  [{:keys [source-files]}]
  (let [namespaces (namespaces source-files)]
    (apply require namespaces)
    (check-result (apply run-tests namespaces))))

; For now: do stuff manually for explicitly named tests.
(defn test-vars
  [{:keys [tests]}]
  (let [tests (group-by (comp symbol namespace) (map read-string tests))]
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
  (cond
    junit       (test-junit/test-namespaces options)
    (seq tests) (test-vars options)
    :else       (test-namespaces options)))
