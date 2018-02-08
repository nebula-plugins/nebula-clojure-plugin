/*-
 * Copyright 2009-2015 © Meikel Brandmeyer.
 * 2018 Netflix, inc. under MIT
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

import org.gradle.api.internal.file.FileResolver
import org.gradle.process.JavaExecSpec
import org.gradle.process.internal.*
import org.gradle.util.GradleVersion

class ClojureExecAction implements JavaExecSpec {
    @Delegate
    JavaExecHandleBuilder base

    ClojureExecAction(FileResolver fileResolver, String gradleVersion) {
        base = create(fileResolver, gradleVersion)
    }

    static JavaExecAction create(FileResolver fileResolver, String gradleVersion) {
        boolean hasExecFactory = GradleVersion.version(gradleVersion) > GradleVersion.version("4.4.1")
        def action
        if (hasExecFactory) {
            action = new DefaultExecActionFactory(fileResolver).newJavaExecAction()
        } else {
            action = new DefaultJavaExecAction(fileResolver)
        }
        action.setMain("-")

        action
    }

    List<String> getAllArguments() {
        List<String> arguments = new ArrayList<>()
        arguments.addAll(getAllJvmArgs())
        arguments.add("clojure.main")

        String m = getMain()
        if ("-".equals(m)) {
            arguments.add("-")
        } else {
            arguments.add("-m")
            arguments.add(m)
        }

        arguments.addAll(getArgs())

        return arguments
    }
}
