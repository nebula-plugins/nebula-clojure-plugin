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

import kotka.gradle.utils.tasks.GenericSourceSet
import org.gradle.api.internal.file.FileResolver

@GenericSourceSet(sourceName="clojure", sourcePatterns=["**/*.clj", "**/*.cljs", "**/*.cljc"])
class ClojureSourceSet {
    def protected initSourceSet(String displayString, FileResolver fileResolver) {
        new ClojureSourceDirectorySet(displayString, fileResolver)
    }
}
