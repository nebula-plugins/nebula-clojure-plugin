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
import nebula.plugin.utils.tasks.SourceDirectoryTask
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService

import javax.inject.Inject

abstract class ClojureSourceTask extends SourceDirectoryTask {
    @Nested
    @Optional
    abstract Property<JavaLauncher> getLauncher()

    @Inject
    ClojureSourceTask() {
        def toolchain = project.getExtensions().getByType(JavaPluginExtension.class).toolchain
        JavaToolchainService service = project.getExtensions().getByType(JavaToolchainService.class)
        Provider<JavaLauncher> defaultLauncher = service.launcherFor(toolchain)
        launcher.convention(defaultLauncher)
    }

    /* Duplicate the functionality of ClojureSourceSet. */
    ClojureSourceTask includeNamespace(String pattern) {
        include(Util.namespaceFile(pattern))
        return this
    }

    ClojureSourceTask excludeNamespace(String pattern) {
        exclude(Util.namespaceFile(pattern))
        return this
    }
}
