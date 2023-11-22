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
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
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

        ClojurePluginExtension extension = project.extensions.create("clojure", ClojurePluginExtension, project)

        def repos = project.repositories
        repos.convention.plugins.clojure =
            new ClojureRepositoryConvention(repos)

        configureSourceSets(project)
        configureCompilation(project, extension)
        configureDocs(project)
        configureTests(project)
        configureRun(project)
        configureClojarsUpload(project)
    }

    private void configureSourceSets(project) {
        project.sourceSets.all { SourceSet sourceSet ->
             def clojureSourceSet =
                new DefaultClojureSourceSet(sourceSet.name, objectFactory)

            sourceSet.convention.plugins.clojure = clojureSourceSet
            sourceSet.clojure.srcDir "src/${sourceSet.name}/clojure"
            sourceSet.allSource.source(clojureSourceSet.clojure)
            sourceSet.allJava.source(clojureSourceSet.clojure)
        }
    }

    private void configureCompilation(Project project, ClojurePluginExtension extension) {
        project.sourceSets.all { SourceSet set ->
            if (set.equals(project.sourceSets.test))
                return
            def compileTaskName = set.getCompileTaskName("clojure")
            TaskProvider<ClojureCompile> task = project.tasks.register(compileTaskName, ClojureCompile)
            task.configure {
                from set.clojure
                aotCompile.set(extension.aotCompile)
                warnOnReflection.set(extension.warnOnReflection)
                classpath.from(
                        set.compileClasspath,
                        project.configurations.findByName('development')?.incoming?.files
                )
                destinationDir.set(
                        findOutputDir(set)
                )
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
            delayedClasspath  = { project.configurations.testRuntimeClasspath }
            delayedOutputDir = { findOutputDir(project.sourceSets.main) }
            delayedJunitOutputDir = {
                project.file(project.layout.buildDirectory.getAsFile().get().path + "/test-results")
            }
            dependsOn project.tasks.classes, project.configurations.testRuntimeClasspath
            description = "Run Clojure tests in src/test."
            group = JavaBasePlugin.VERIFICATION_GROUP
            if (project.hasProperty("clojuresque.test.vars")) {
                tests = project.getProperty("clojuresque.test.vars").split(",")
            }
        }
        project.tasks.test.dependsOn clojureTest
        if (project.gradle.startParameter.taskNames.contains('--tests')) {
            project.tasks.clojureTest.configure {
                enabled = false
            }
        }
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
        project.tasks.withType(Upload).configureEach { Upload upload ->
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
