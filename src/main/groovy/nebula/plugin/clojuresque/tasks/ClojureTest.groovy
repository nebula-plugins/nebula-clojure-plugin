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

import kotka.gradle.utils.ConfigureUtil
import kotka.gradle.utils.Delayed
import nebula.plugin.clojuresque.Util
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskAction

class ClojureTest extends ClojureSourceTask {
    @Delayed
    @Internal
    def outputDir

    @InputFiles
    @Classpath
    @Delayed
    def classpath

    @Internal
    @Delayed
    def jvmOptions = {}

    @Input
    def junit = false

    @OutputDirectory
    @Delayed
    def junitOutputDir = null

    @Internal
    def tests = []

    @TaskAction
    void runTests() {
        def junitDir = getJunitOutputDir()
        if (junit) {
            if (junitDir == null) {
                throw new StopExecutionException("junitOutputDir is not set!")
            }
            junitDir.mkdirs()
        }

        def options = [
            sourceFiles:    source.files*.path,
            tests:          tests,
            junit:          junit,
            junitOutputDir: junitDir?.path,
        ]

        def runtime = [
            "clojuresque/util.clj",
            "clojuresque/tasks/test_junit.clj",
            "clojuresque/tasks/test.clj"
        ].collect { owner.class.classLoader.getResourceAsStream(it) }

        project.javaexec {
            setMain("clojure.main")
            args('-')
            ConfigureUtil.configure delegate, this.jvmOptions
            classpath = project.files(
                this.srcDirs,
                this.outputDir,
                this.classpath
            )
            standardInput = Util.toInputStream([
                runtime,
                "(clojuresque.tasks.test/main)",
                Util.optionsToStream(options)
            ])
        }
    }
}
