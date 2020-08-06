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
import org.gradle.api.tasks.bundling.Jar

public class ClojureExtrasUberJarPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.tasks.withType(Jar).asMap.each { name, jar ->
            project.task("uber" + name, type: Jar) {
                description =
                    'Constructs a jar with all runtime dependencies included'
                group = "other"
                dependsOn jar.source, project.configurations.runtimeClasspath
                String baseName = jar.archiveBaseName.getOrNull() + '-standalone'
                archiveBaseName.convention baseName
                archiveBaseName.set baseName
                enabled = false
                doFirst {
                    project.configurations.runtimeClasspath.each {
                        from project.zipTree(it)
                        exclude 'META-INF/MANIFEST.MF'
                        exclude 'META-INF/*.SF'
                        exclude 'META-INF/*.DSA'
                        exclude 'META-INF/*.RSA'
                    }
                    from jar.source
                }
            }
        }
    }
}
