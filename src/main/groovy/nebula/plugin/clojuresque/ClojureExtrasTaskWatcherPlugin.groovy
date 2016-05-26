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

import nebula.plugin.clojuresque.tasks.TaskWatcher

import org.gradle.api.Plugin
import org.gradle.api.Project

public class ClojureExtrasTaskWatcherPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.task("watchTasks", type: TaskWatcher) {
            description = "Run watched tasks continually"
            group = "other"
        }
    }
}
