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

import org.gradle.api.internal.file.DefaultFileCollectionFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec
import org.gradle.process.internal.*
import org.gradle.util.GradleVersion

class ClojureExecAction implements JavaExecAction, JavaExecSpec {
    private static final String GRADLE_FIVE_THREE = "5.3"

    @Delegate
    JavaExecAction base

    ClojureExecAction(FileResolver fileResolver, String gradleVersion) {
        base = create(fileResolver, gradleVersion)
    }

    static JavaExecAction create(FileResolver fileResolver, String gradleVersion) {
        boolean isGradleFiveThreeOrHigher = GradleVersion.version(gradleVersion) > GradleVersion.version(GRADLE_FIVE_THREE) || gradleVersion.startsWith(GRADLE_FIVE_THREE)
        boolean hasExecFactory = GradleVersion.version(gradleVersion) > GradleVersion.version("4.4.1")
        def action
        if(isGradleFiveThreeOrHigher) {
            IdentityFileResolver resolver = new IdentityFileResolver()
            action = DefaultExecActionFactory.of(fileResolver,  new DefaultFileCollectionFactory(resolver, (TaskResolver)null), new DefaultExecutorFactory(), new DefaultBuildCancellationToken()).newJavaExecAction()
        } else if (hasExecFactory) {
            action = new DefaultExecActionFactory(fileResolver).newJavaExecAction()
        } else {
            action = new DefaultJavaExecAction(fileResolver)
        }
        action.setMain("clojure.main")
        action.args('-')

        action
    }

    @Override
    JavaExecSpec setMain(String var1) {
        throw new RuntimeException("this shouldn't be called for " + var1 + "and " + base.getArgs())
        if (var1 == '-') {
            base.setMain('clojure.main')
            List<String> args = base.getArgs()
            if (args[0] == '-m') {
                args.remove(1)
                args.remove(0)
                base.setArgs(["-"] + args)
            } else if (args[0] != '-') {
                base.setArgs(["-"] + args)
            }
        } else {
            base.setMain("clojure.main")
            List<String> args = base.getArgs()
            if (args[0] == '-m') {
                args.remove(1)
                args.remove(0)
            } else if (args[0] == '-') {
                args.remove(0)
            }
            base.setArgs(['-m', var1] + args)
        }
    }
}
