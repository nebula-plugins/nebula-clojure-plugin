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

package nebula.plugin.clojuresque

import nebula.plugin.clojuresque.tasks.ClojureCompile
import nebula.plugin.clojuresque.tasks.ClojureDoc
import nebula.plugin.clojuresque.tasks.ClojureRun
import nebula.plugin.clojuresque.tasks.ClojureTest
import nebula.plugin.clojuresque.tasks.ClojureUploadConvention
import nebula.plugin.clojuresque.tasks.DefaultClojureSourceSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.Upload

import javax.inject.Inject

class ClojureBasePlugin implements Plugin<Project> {
    static final String CLOJURE_GROUP = "clojure development"

    private final ObjectFactory objectFactory

    @Inject
    ClojureBasePlugin(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory
    }

    void apply(Project project) {
        project.apply plugin: JavaPlugin
        project.apply plugin: ClojureCommonPlugin

        project.convention.plugins.clojureDeprecated =
            new ClojurePluginDeprecatedConvention(project)

        project.extensions.create("clojure", ClojurePluginExtension)

        def repos = project.repositories
        repos.convention.plugins.clojure =
            new ClojureRepositoryConvention(repos)

        configureSourceSets(project)
        configureCompilation(project)
        configureDocs(project)
        configureTests(project)
        configureRun(project)
        configureClojarsUpload(project)
    }

    private void configureSourceSets(project) {
        project.sourceSets.all { sourceSet ->
             def clojureSourceSet =
                new DefaultClojureSourceSet(sourceSet.name, objectFactory)

            sourceSet.convention.plugins.clojure = clojureSourceSet
            sourceSet.clojure.srcDir "src/${sourceSet.name}/clojure"
            sourceSet.allSource.source(clojureSourceSet.clojure)
        }
    }

    private void configureCompilation(project) {
        project.sourceSets.all { set ->
            if (set.equals(project.sourceSets.test))
                return
            def compileTaskName = set.getCompileTaskName("clojure")
            def task = project.task(compileTaskName, type: ClojureCompile) {
                from set.clojure
                delayedAotCompile       = { project.clojure.aotCompile }
                delayedWarnOnReflection = { project.clojure.warnOnReflection }
                delayedDestinationDir   = { findOutputDir(set) }
                delayedClasspath = {
                    project.files(
                        set.compileClasspath,
                        project.configurations.development
                    )
                }
                description = "Compile the ${set.name} Clojure source."
            }
            project.tasks[set.classesTaskName].dependsOn task
        }
    }

    private void configureDocs(project) {
        project.sourceSets.main { set ->
            def compileTaskName = set.getCompileTaskName("clojure")
            def docTaskName = set.getTaskName(null, "clojuredoc")
            def compileTask = project.tasks[compileTaskName]
            def task = project.task(docTaskName, type: ClojureDoc) {
                from set.clojure
                delayedDestinationDir = {
                    project.file(project.docsDir.path + "/clojuredoc")
                }
                delayedJvmOptions = { compileTask.jvmOptions }
                delayedClasspath = { compileTask.classpath }
                description =
                    "Generate documentation for the Clojure source."
                group = JavaBasePlugin.DOCUMENTATION_GROUP
            }
        }
    }

    private void configureTests(project) {
        def compileTask = project.tasks[
            project.sourceSets.main.getCompileTaskName("clojure")
        ]
        def clojureTest = project.task("clojureTest", type: ClojureTest) {
            from project.sourceSets.test.clojure
            delayedJvmOptions = { compileTask.jvmOptions }
            delayedClasspath  = { project.configurations.testRuntime }
            delayedOutputDir = { findOutputDir(project.sourceSets.main) }
            delayedJunitOutputDir = {
                project.file(project.buildDir.path + "/test-results")
            }
            dependsOn project.tasks.classes, project.configurations.testRuntime
            description = "Run Clojure tests in src/test."
            group = JavaBasePlugin.VERIFICATION_GROUP
            if (project.hasProperty("clojuresque.test.vars")) {
                tests = project.getProperty("clojuresque.test.vars").split(",")
            }
        }
        project.tasks.test.dependsOn clojureTest
    }

    private void configureRun(project) {
        project.sourceSets.main { set ->
            def compileTaskName = set.getCompileTaskName("clojure")
            def runTaskName = set.getTaskName(null, "clojureRun")
            def compileTask = project.tasks[compileTaskName]
            def task = project.task(runTaskName, type: ClojureRun) {
                from set.clojure
                delayedJvmOptions = { compileTask.jvmOptions }
                delayedClasspath = { compileTask.classpath }
                description = "Run a Clojure command."
                group = CLOJURE_GROUP
            }
        }
    }

    private void configureClojarsUpload(project) {
        project.tasks.whenTaskAdded { upload ->
            if (!(upload instanceof Upload))
                return
            upload.convention.plugins.clojure =
                new ClojureUploadConvention(upload)
        }
    }

    private File findOutputDir(SourceSet set) {
        return set.output.classesDirs.files.find {
            it.path.contains('clojure/main') || it.path.contains('java/main')
        }
    }
}
