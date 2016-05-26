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
import org.gradle.api.tasks.Copy

public class ClojureExtrasDepsPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.task("deps", type: Copy) {
            description =
                'Copy runtime dependencies into the "lib" directory'
            group = "other"
            into 'lib'
            from project.configurations.runtime
        }
    }
}
