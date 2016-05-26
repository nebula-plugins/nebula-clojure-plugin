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

import nebula.plugin.clojuresque.tasks.ClojureExecAction
import kotka.gradle.utils.ConfigureUtil

import org.gradle.api.Project
import org.gradle.process.ExecResult

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ClojurePluginConvention {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClojurePluginConvention)
    private final Project project

    public ClojurePluginConvention(Project project) {
        this.project = project
    }

    public ExecResult clojureexec(Closure spec) {
        ClojureExecAction action = ConfigureUtil.configure(
            new ClojureExecAction(project.fileResolver),
            spec
        )
        return action.execute()
    }
}
