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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

import javax.inject.Inject

@CacheableTask
abstract class ClojureTest extends ClojureSourceTask {
    @Internal
    abstract Property<File> getOutputDir()

    @InputFiles
    @Classpath
    abstract ConfigurableFileCollection getClasspath()

    @Input
    abstract Property<Boolean> getJunit()

    @OutputDirectory
    @Optional
    abstract Property<File> getJunitOutputDir()

    @Internal
    abstract ListProperty<String> getTests()

    private final ExecOperations execOperations

    private final ObjectFactory objects


    @Inject
    ClojureTest(ExecOperations execOperations, ObjectFactory objects) {
        this.execOperations = execOperations
        this.objects = objects
    }

    @TaskAction
    void runTests() {
        def junitDir = junitOutputDir.isPresent() ? junitOutputDir.get() : null
        if (junit.isPresent() && junit.get()) {
            if (junitDir == null) {
                throw new StopExecutionException("junitOutputDir is not set!")
            }
            junitDir.mkdirs()
        }

        def options = [
            sourceFiles:    source.files*.path,
            tests:          tests.getOrElse([]).join(","),
            junit:          junit.get(),
            junitOutputDir: junitDir?.path,
        ]

        def runtime = [
            "clojuresque/util.clj",
            "clojuresque/tasks/test_junit.clj",
            "clojuresque/tasks/test.clj"
        ].collect { owner.class.classLoader.getResourceAsStream(it) }

        def objectFactory = objects
        execOperations.javaexec {
            setMainClass("clojure.main")
            args('-')
            classpath = objectFactory.fileCollection().from(
                this.srcDirs,
                this.outputDir,
                this.classpath
            )
            standardInput = Util.toInputStream([
                runtime,
                "(clojuresque.tasks.test/main)",
                Util.optionsToStream(options)
            ])
            if(launcher.isPresent()) {
                setExecutable(launcher.get().getExecutablePath().getAsFile().getAbsolutePath())
            }
        }
    }
}
