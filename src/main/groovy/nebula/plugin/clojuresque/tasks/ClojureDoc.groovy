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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

import javax.inject.Inject

@CacheableTask
abstract class ClojureDoc extends ClojureSourceTask {
    @OutputDirectory
    @Optional
    abstract Property<File> getDestinationDir()

    @InputFiles
    @Classpath
    abstract ConfigurableFileCollection getClasspath()

    @Input
    @Optional
    abstract MapProperty<String, Object> getCodox()

    @Input
    abstract Property<String> getProjectName()

    @Input
    abstract Property<String> getProjectDescription()

    @Input
    abstract Property<String> getProjectVersion()

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract DirectoryProperty getProjectDirectory()

    private final ExecOperations execOperations
    private final ObjectFactory objectFactory

    @Inject
    ClojureDoc(ExecOperations execOperations, ObjectFactory objectFactory) {
        this.execOperations = execOperations
        this.objectFactory = objectFactory
        codox.convention([:])
        projectName.convention("")
        projectDescription.convention("")
        projectVersion.convention("")
    }

    @TaskAction
    void clojuredoc() {
        if (!destinationDir.isPresent() || destinationDir.get() == null) {
            throw new StopExecutionException("destinationDir not set!")
        }
        def destDir = destinationDir.get()
        destDir.mkdirs()

        def options = [
            destinationDir:  destDir.path,
            project: [
                name:        projectName.get(),
                description: projectDescription.get(),
                version:     projectVersion.get()
            ],
            codox:           codox.get(),
            sourceDirs:      srcDirs.files.collect {
                relativize(it, projectDirectory.get().asFile)
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

        execOperations.javaexec {
            setMainClass("clojure.main")
            args('-')
            classpath = objectFactory.fileCollection().from(
                this.srcDirs,
                this.classpath
            )
            standardInput = Util.toInputStream([
                runtime,
                "(clojuresque.tasks.doc/main)",
                Util.optionsToStream(options)
            ])
            if(launcher.isPresent()) {
                setExecutable(launcher.get().getExecutablePath().getAsFile().getAbsolutePath())
            }
        }

        [
            "css/default.css",
            "js/page_effects.js",
            "js/jquery.min.js"
        ].each { f ->
            def dest = new File(destinationDir.get(), f)
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
