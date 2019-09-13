/*-
 * Copyright 2011-2015 © Meikel Brandmeyer.
 * All rights reserved.
 *
 * Licensed under the EUPL V.1.1 (cf. file EUPL-1.1 distributed with the
 * source code.) Translations in other european languages available at
 * https://joinup.ec.europa.eu/software/page/eupl.
 *
 * Alternatively, you may choose to use the software under the MIT license
 * (cf. file MIT distributed with the source code).
 */

package nebula.plugin.clojuresque.tasks

import nebula.plugin.clojuresque.Util

import kotka.gradle.utils.ConfigureUtil
import kotka.gradle.utils.Delayed
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskAction

class ClojureDoc extends ClojureSourceTask {
    @OutputDirectory
    @Delayed
    def destinationDir

    @Classpath
    @Delayed
    def classpath

    @Delayed
    @Input
    @Optional
    def jvmOptions

    @Input
    @Optional
    def codox = [:]

    @TaskAction
    void clojuredoc() {
        def destDir = getDestinationDir()
        if (destDir == null) {
            throw new StopExecutionException("destinationDir not set!")
        }
        destDir.mkdirs()

        def options = [
            destinationDir:  destDir.path,
            project: [
                name:        project.name ?: "",
                description: project.description ?: "",
                version:     project.version ?: ""
            ],
            codox:           codox,
            sourceDirs:      srcDirs.files.collect {
                relativize(it, project.projectDir)
            },
            sourceFiles:     source*.path
        ]

        def runtime = [
            "clojuresque/util.clj",
            "clojuresque/hiccup/util.clj",
            "clojuresque/hiccup/compiler.clj",
            "clojuresque/hiccup/core.clj",
            "clojuresque/hiccup/def.clj",
            "clojuresque/hiccup/element.clj",
            "clojuresque/hiccup/page.clj",
            "clojuresque/codox/utils.clj",
            "clojuresque/codox/reader.clj",
            "clojuresque/codox/main.clj",
            "clojuresque/codox/writer/html.clj",
            "clojuresque/tasks/doc.clj"
        ].collect { owner.class.classLoader.getResourceAsStream it }

        project.javaexec {
            setMain("clojure.main")
            args('-')
            ConfigureUtil.configure delegate, this.jvmOptions
            classpath = project.files(
                this.srcDirs,
                this.classpath
            )
            standardInput = Util.toInputStream([
                runtime,
                "(clojuresque.tasks.doc/main)",
                Util.optionsToStream(options)
            ])
        }

        [
            "css/default.css",
            "js/page_effects.js",
            "js/jquery.min.js"
        ].each { f ->
            def dest = project.file("${destinationDir}/${f}")
            println "${f}"
            if (!dest.exists()) {
                dest.parentFile.mkdirs()
                dest.withOutputStream { output ->
                    def input = this.class.classLoader.
                        getResourceAsStream("clojuresque/codox/${f}")
                    output << input
                    input.close()
                }
            }
        }
    }

    def relativize(path, projectDir) {
        def pathS = path.path
        def pdirS = projectDir.path

        pathS.substring(pdirS.length() + 1)
    }
}
