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
import nebula.plugin.clojuresque.tasks.DefaultClojureSourceSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider

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

        ClojurePluginExtension extension = project.extensions.create("clojure", ClojurePluginExtension, project)

        def repos = project.repositories

        JavaPluginExtension javaPluginExtension = project.extensions.findByType(JavaPluginExtension)
        if (!javaPluginExtension) {
            return
        }
        configureSourceSets(project, javaPluginExtension)
        configureCompilation(project, extension, javaPluginExtension)
        configureDocs(project, javaPluginExtension)
        configureTests(project, javaPluginExtension)
        configureRun(project, javaPluginExtension)
    }

    private void configureSourceSets(project, JavaPluginExtension javaPluginExtension) {
        javaPluginExtension.sourceSets.all { SourceSet sourceSet ->
             def clojureSourceSet =
                new DefaultClojureSourceSet(sourceSet.name, objectFactory)

            sourceSet.extensions.add(DefaultClojureSourceSet.class, "clojure", clojureSourceSet)
            clojureSourceSet.getClojure().srcDir "src/${sourceSet.name}/clojure"
            sourceSet.allSource.source(clojureSourceSet.clojure)
            sourceSet.allJava.source(clojureSourceSet.clojure)
        }
    }

    private void configureCompilation(Project project, ClojurePluginExtension extension, JavaPluginExtension javaPluginExtension) {
        javaPluginExtension.sourceSets.all { SourceSet set ->
            if (set.equals(javaPluginExtension.sourceSets.test))
                return
            def compileTaskName = set.getCompileTaskName("clojure")
            TaskProvider<ClojureCompile> task = project.tasks.register(compileTaskName, ClojureCompile)
            task.configure {
                from project.file("src/${set.name}/clojure")
                aotCompile.set(extension.aotCompile)
                warnOnReflection.set(extension.warnOnReflection)
                classpath.from(set.compileClasspath)
                def developmentConfig = project.configurations.findByName('development')
                if (developmentConfig != null) {
                    classpath.from(developmentConfig)
                }
                destinationDir.set(
                        findOutputDir(set)
                )
                description = "Compile the ${set.name} Clojure source."
            }
            project.tasks.named(set.classesTaskName).configure {
                dependsOn task
            }
        }
    }

    private void configureDocs(Project project, JavaPluginExtension javaPluginExtension) {
        javaPluginExtension.sourceSets.main { SourceSet set ->
            def docTaskName = set.getTaskName(null, "clojuredoc")
            TaskProvider<ClojureDoc> task = project.tasks.register(docTaskName, ClojureDoc)
            task.configure {
                from project.file("src/${set.name}/clojure")

                destinationDir.set(project.file(javaPluginExtension.docsDir.dir("clojuredoc")))
                classpath.from(
                        set.compileClasspath
                )
                projectName.set(project.name)
                projectDescription.set(project.provider { project.description ?: "" })
                projectVersion.set(project.provider { project.version?.toString() ?: "" })
                projectDirectory.set(project.layout.projectDirectory)
                description = "Generate documentation for the Clojure source."
                group = JavaBasePlugin.DOCUMENTATION_GROUP
            }
        }
    }

    private void configureTests(Project project, JavaPluginExtension javaPluginExtension) {
        TaskProvider<ClojureTest> clojureTest = project.tasks.register('clojureTest', ClojureTest)
        clojureTest.configure {
            from project.file("src/test/clojure")
            classpath.from(
                    project.configurations.testRuntimeClasspath.incoming.files
            )
            outputDir.set(
                    findOutputDir(javaPluginExtension.sourceSets.main)
            )
            junit.convention(false)
            junitOutputDir.set(project.layout.buildDirectory.dir("test-results").getOrNull()?.asFile)
            dependsOn project.tasks.classes, project.configurations.testRuntimeClasspath
            description = "Run Clojure tests in src/test."
            group = JavaBasePlugin.VERIFICATION_GROUP
            if (project.hasProperty("clojuresque.test.vars")) {
                tests.set(project.property("clojuresque.test.vars").split(",").toList())
            }
            if (project.gradle.startParameter.taskNames.contains('--tests')) {
                enabled = false
            }
        }
        project.tasks.named('test').configure {
            dependsOn clojureTest
        }
    }

    private void configureRun(Project project, JavaPluginExtension javaPluginExtension) {
        javaPluginExtension.sourceSets.main { SourceSet set ->
            def runTaskName = set.getTaskName(null, "clojureRun")
            TaskProvider<ClojureRun> task = project.tasks.register(runTaskName, ClojureRun)
            task.configure {
                from project.file("src/${set.name}/clojure")
                classpath.from(
                        set.compileClasspath
                )
                description = "Run a Clojure command."
                group = CLOJURE_GROUP
            }
        }
    }


    private File findOutputDir(SourceSet set) {
        return set.output.classesDirs.files.find {
            it.path.contains('clojure/main') || it.path.contains('java/main')
        }
    }
}
