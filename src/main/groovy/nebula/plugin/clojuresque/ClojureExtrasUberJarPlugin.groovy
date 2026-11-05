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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.bundling.Jar

import javax.inject.Inject

class ClojureExtrasUberJarPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.tasks.withType(Jar).asMap.each { name, jar ->
            project.tasks.register('uber' + name, Jar).configure {
                def runtimeClasspath = project.configurations.runtimeClasspath
                def archives = project.objects.newInstance(RuntimeArchives)
                description = 'Constructs a jar with all runtime dependencies included'
                group = "other"
                dependsOn jar.source, runtimeClasspath
                archiveBaseName.set(jar.archiveBaseName.map { it + '-standalone' })
                enabled = false

                inputs.files(runtimeClasspath).withPropertyName('runtimeClasspath')
                    .withNormalizer(ClasspathNormalizer)
                from(runtimeClasspath.elements.map(archives))
                exclude 'META-INF/MANIFEST.MF'
                exclude 'META-INF/*.SF'
                exclude 'META-INF/*.DSA'
                exclude 'META-INF/*.RSA'
                from jar.source
            }
        }
    }

    static class RuntimeArchives implements Transformer<List<FileTree>, Set<FileSystemLocation>> {
        private final ArchiveOperations archives

        @Inject
        RuntimeArchives(ArchiveOperations archives) {
            this.archives = archives
        }

        @Override
        List<FileTree> transform(Set<FileSystemLocation> locations) {
            def archiveOperations = archives
            locations.collect { archiveOperations.zipTree(it.asFile) }
        }
    }
}
