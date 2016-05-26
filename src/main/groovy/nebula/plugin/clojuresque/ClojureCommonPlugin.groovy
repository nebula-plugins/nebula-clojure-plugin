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

import org.gradle.api.Plugin
import org.gradle.api.Project

public class ClojureCommonPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.convention.plugins.clojure =
            new ClojurePluginConvention(project)

        project.configurations {
            development {
                transitive = true
                visible = false
                description = "Development only dependencies"
            }
        }
    }
}
