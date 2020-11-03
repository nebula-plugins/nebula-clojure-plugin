/*-
 * Copyright 2009-2015 © Meikel Brandmeyer.
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

import clojure.lang.RT
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

class ClojureCompile extends ClojureSourceTask {
    @OutputDirectory
    @Delayed
    def destinationDir

    @InputFiles
    @Classpath
    @Delayed
    def classpath

    @Input
    @Delayed
    def aotCompile = false

    @Input
    @Delayed
    def warnOnReflection = false

    @Internal
    def dirMode  = null
    @Internal
    def fileMode = null

    @Internal
    @Delayed
    def jvmOptions = {}

    @TaskAction
    void compile(IncrementalTaskInputs inputs) {
        def destDir = getDestinationDir()
        if (destDir == null) {
            throw new StopExecutionException("destinationDir not set!")
        }
        destDir.mkdirs()

        def final require = RT.var("clojure.core", "require")
        def final symbol  = RT.var("clojure.core", "symbol")

        require.invoke(symbol.invoke("clojuresque.tasks.clojure-compile-util"))

        def final fileDependencies = RT.var(
            "clojuresque.tasks.clojure-compile-util",
            "file-dependencies"
        )

        def dependencyGraph = fileDependencies.invoke(source.files)

        def outOfDateInputs = [] as Set
        inputs.outOfDate {
            if (it.file.path.endsWith(".clj"))
                outOfDateInputs << it.file
        }
        inputs.removed {
            if (it.file.path.endsWith(".clj"))
                deleteDerivedFiles(it.file)
        }

        def toCompile = findDependentFiles(outOfDateInputs, dependencyGraph)

        def options = [
            compileMode:      (getAotCompile()) ? "compile" : "require",
            warnOnReflection: (getWarnOnReflection()),
            sourceFiles:      toCompile.collect { it.path }
        ]

        def runtime = [
            "clojuresque/util.clj",
            "clojuresque/tasks/compile.clj"
        ].collect { owner.class.classLoader.getResourceAsStream it }

        project.javaexec {
            setMain("clojure.main")
            args('-')
            ConfigureUtil.configure delegate, this.jvmOptions
            systemProperties "clojure.compile.path": destDir.path
            classpath = project.files(
                this.srcDirs,
                destDir,
                this.classpath
            )
            standardInput = Util.toInputStream([
                runtime,
                "(clojuresque.tasks.compile/main)",
                Util.optionsToStream(options)
            ])
        }

        if (!getAotCompile()) {
            project.copy {
                dirMode  = this.dirMode
                fileMode = this.fileMode

                from(srcDirs) {
                    include {
                        def f = it.file
                        f.isDirectory() || outOfDateInputs.contains(f)
                    }
                }
                into destDir
            }
        }
    }

    def findDependentFiles(outOfDateFiles, dependencyGraph) {
        def toCompile = [] as Set
        outOfDateFiles.each {
            toCompile << it
            dependencyGraph[it].each { dep -> toCompile << dep }
        }
        toCompile
    }

    def deleteDerivedFiles(parent) {
        def relativeParent = getSrcDirs().findResult {
            Util.relativizePath(it, parent)
        }
        if (relativeParent == null)
            return

        def pattern = relativeParent.replaceAll("\\.clj\$", "") + "*"

        project.fileTree(getDestinationDir()).include(pattern).files.each {
            it.delete()
        }
    }
}
