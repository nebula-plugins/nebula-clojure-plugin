;-
; Copyright 2009-2015 © Meikel Brandmeyer.
; All rights reserved.
;
; Licensed under the EUPL V.1.1 (cf. file EUPL-1.1 distributed with the
; source code.) Translations in other european languages available at
; https://joinup.ec.europa.eu/software/page/eupl.
;
; Alternatively, you may choose to use the software under the MIT license
; (cf. file MIT distributed with the source code).

(ns clojuresque.util
  (:import
    clojure.lang.LineNumberingPushbackReader)
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(defn namespace-of-file
  [file]
  (let [of-interest '#{ns clojure.core/ns in-ns clojure.core/in-ns}
        eof         (Object.)
        input       (LineNumberingPushbackReader. (io/reader file))
        in-seq      (take-while #(not (identical? % eof))
                                (repeatedly #(read input false eof)))
        candidate   (first
                      (drop-while
                        #(or (not (instance? clojure.lang.ISeq %))
                             (not (contains? of-interest (first %))))
                        in-seq))]
    (when candidate
      (second candidate))))

(defn namespaces
  [files]
  (distinct (keep namespace-of-file files)))

(defn safe-require
  [& nspaces]
  (binding [*unchecked-math*     *unchecked-math*
            *warn-on-reflection* *warn-on-reflection*]
    (apply require nspaces)))

(defn resolve-required
  [fully-qualified-sym]
  (let [slash  (.indexOf ^String fully-qualified-sym "/")
        nspace (symbol (subs fully-qualified-sym 0 slash))
        hfn    (symbol (subs fully-qualified-sym (inc slash)))]
    (safe-require nspace)
    (ns-resolve nspace hfn)))

(defmacro deftask
  [task-name & fntail]
  `(let [driver# (fn ~(symbol (str (name task-name) "-task-driver"))
                   ~fntail)]
     (defn ~task-name
       []
       (let [options# (edn/read (LineNumberingPushbackReader. *in*))]
         (driver# options#)))))
