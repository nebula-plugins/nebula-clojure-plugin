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

import org.gradle.api.Project
import org.gradle.api.provider.Property

abstract class ClojurePluginExtension {
    private final Project project

    ClojurePluginExtension(Project project) {
        this.project = project
    }
    Property<Boolean> warnOnReflection = project.objects.property(Boolean).convention(false)

    Property<Boolean> aotCompile = project.objects.property(Boolean).convention(false)
}
