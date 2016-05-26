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

package nebula.plugin.clojuresque.tasks;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.JavaExecAction;
import org.gradle.process.internal.JavaExecHandleBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ClojureExecAction extends JavaExecHandleBuilder implements JavaExecAction {
    public ClojureExecAction(FileResolver fileResolver) {
        super(fileResolver);
        setMain("-");
    }

    @Override
    public List<String> getAllArguments() {
        List<String> arguments = new ArrayList<String>();
        arguments.addAll(getAllJvmArgs());
        arguments.add("clojure.main");

        String m = getMain();
        if ("-".equals(m)) {
            arguments.add("-");
        } else {
            arguments.add("-m");
            arguments.add(m);
        }

        arguments.addAll(getArgs());

        return arguments;
    }

    public ExecResult execute() {
        ExecHandle execHandle = build();
        ExecResult execResult = execHandle.start().waitForFinish();
        if (!isIgnoreExitValue()) {
            execResult.assertNormalExitValue();
        }
        return execResult;
    }
}
