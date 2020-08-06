/*-
 * Copyright 2013-2015 © Meikel Brandmeyer.
 * All rights reserved.
 *
 * Licensed under the EUPL V.1.1 (cf. file EUPL-1.1 distributed with the
 * source code.) Translations in other european languages available at
 * https://joinup.ec.europa.eu/software/page/eupl.
 *
 * Alternatively, you may choose to use the software under the MIT license
 * (cf. file MIT distributed with the source code).
 */

package nebula.plugin.clojuresque

import nebula.plugin.clojuresque.tasks.ClojureRepl

import org.gradle.api.Plugin
import org.gradle.api.Project

class ClojureReplPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.apply plugin: ClojureBasePlugin

        def compileTaskName =
            project.sourceSets.main.getCompileTaskName("clojure")
        def compileTask = project.tasks[compileTaskName]

        project.task("clojureRepl", type: ClojureRepl) {
            port = 7888
            delayedJvmOptions = { compileTask.jvmOptions }
            delayedClasspath  = {
                def sourceRoots = project.sourceSets.collect {
                    it.allSource.srcDirs
                }

                project.files(
                    sourceRoots,
                    project.sourceSets.collect { it.output },
                    project.configurations.testRuntimeClasspath,
                    project.configurations.development
                )
            }
            description = "Run Clojure repl."
            group = ClojureBasePlugin.CLOJURE_GROUP
        }
    }
}
