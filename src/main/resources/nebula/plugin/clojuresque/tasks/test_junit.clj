(ns clojuresque.tasks.test-junit
  (:use
    [clojure.test :only (run-tests report successful? *test-out*) :as t]
    [clojure.test.junit :only (junit-report with-junit-output) :as j]))

(refer 'clojuresque.util :only '[namespaces])

(def escape-xml-map
  (zipmap "'<>\"&" (map #(str \& % \;) '[apos lt gt quot amp])))

(defn- escape-xml [text]
  (apply str (map #(escape-xml-map % %) text)))

(defn xml-escaping-writer
  [writer]
  (proxy
    [java.io.FilterWriter] [writer]
    (write [text]
      (if (string? text)
        (.write writer (escape-xml text))
        (.write writer text)))))


(defn add-counters [results counters]
  (merge-with + results counters))

(defn check-result
  [result]
  (when (or (pos? (:fail result)) (pos? (:error result)))
    (System/exit 1)))

(defn escape-file-path 
  "Escapes the given file path so that it's safe for inclusion in a Clojure string literal."
  [directory file]
  (-> (java.io.File. directory file)
    (.getPath)
    (.replace "\\" "\\\\")))

(defn test-namespace-with-junit-output
  "Run all tests in the namespace with junit output.
   Writes test output to a file called <namespace>.xml in <output-dir>
   XML escapes *out* so that it's safe for inclusion in the JUnit XML report file." 
  [namespace output-dir]
  (with-open [writer (clojure.java.io/writer (str (escape-file-path output-dir (str namespace ".xml"))))
              escaped (xml-escaping-writer writer)]
    (binding [*test-out* writer *out* escaped]
      (with-junit-output
        (run-tests namespace)))))
  
(defn test-namespaces
  [{:keys [junit-output-dir source-files]}]
  (let [namespaces (namespaces source-files)]
    (apply require namespaces)
    (let [results (atom {:type :summary})
          current-ns (atom nil)
          failed (atom [])
          report-orig report
          junit-report-orig junit-report]
      ; Change junit-report so that it also prints to System/out and records summaries for each namespace tested 
      (binding [junit-report (fn [x] 
                               (junit-report-orig x)
                               (when (or (= :begin-test-ns (:type x)) (= :summary (:type x)))
                                 (binding [*test-out* (java.io.OutputStreamWriter. System/out)]
                                   (report-orig x)))
                               ; Record results for each namespace for later result checking and reporting
                               (when (= :begin-test-ns (:type x))
                                 (reset! current-ns (ns-name (:ns x))))
                               (when (= :summary (:type x))
                                 (when (or (pos? (:fail x)) (pos? (:error x)))
                                   (swap! failed conj [@current-ns x]))
                                 (swap! results add-counters (dissoc x :type))))]
        ; test each namespace individually to allow per ns reporting of failures at the end
        (doseq [namespace namespaces]
          (test-namespace-with-junit-output namespace junit-output-dir))
        (if (:test @results)
          (do
            (println "\nTotals:")
            (report @results)
            (println)
            (if (successful? @results)
              (do
                (println "Success!!!")
                true)
              (do
                (println "\n!!! There were test failures:")
                ; Print results for each namespace which was unsuccessful
                (doseq [[ns summary] @failed]
                  (println ns ": " (:fail summary) "failures," (:error summary) "errors."))
                (println)
                false)))
          true)))))
