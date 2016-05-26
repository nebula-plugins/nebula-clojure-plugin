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

import kotka.gradle.utils.Delayed

import org.gradle.api.internal.file.DefaultSourceDirectorySet

@groovy.transform.InheritConstructors
class ClojureSourceDirectorySet extends DefaultSourceDirectorySet {
    @Delayed
    def aotCompile = false

    @Delayed
    def warnOnReflection = false

    void includeNamespace(String pattern) {
        include(
            pattern.replaceAll("-", "_").replaceAll("\\.", "/") + ".clj"
        )
    }

    void excludeNamespace(String pattern) {
        exclude(
            pattern.replaceAll("-", "_").replaceAll("\\.", "/") + ".clj"
        )
    }
}
