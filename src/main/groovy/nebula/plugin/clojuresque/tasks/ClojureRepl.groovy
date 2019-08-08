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

package nebula.plugin.clojuresque.tasks

import kotka.gradle.utils.ConfigureUtil
import kotka.gradle.utils.Delayed
import nebula.plugin.clojuresque.Util
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

/**
 * Starts a nrepl server for the project.
 *
 * To provide your own handler, eg. to use custom nrepl middlewares,
 * put the code into a separate sourceSet and tell the task to use
 * your specific handler factory.
 *
 * <pre><code> sourceSets { dev }
 *
 * clojureRepl {
 *      handler = "my.repl/handler"
 * }
 * </code></pre>
 *
 * And in <code>src/dev/clojure/my/repl.clj</code>:
 * <pre><code> (ns my.repl
 *   (:require
 *     [clojure.tools.nrepl.server :as repl]))
 *
 * (defn handler
 *   []
 *   (repl/default-handler #'my-middleware))
 * </code></pre>
 *
 * If all you want is to specify custom middleware, there is a
 * short-hand in the <code>middleware</code> option. Here you can
 * specify the fully-qualified names of the middleware in the desired
 * order.
 *
 * <pre><code> clojureRepl {
 *     middleware &lt;&lt; "my.repl/middleware"
 * }
 * </code></pre>
 *
 * Some environments need certain support code to be initialized from
 * the classpath. For example David Greenberg's redl or Petit Laurent's
 * counterclockwise. You can specify such namespaces via the
 * <code>injections</code> option.
 *
 * <pre><code> clojureRepl {
 *     injections = [ "redl.core", "redl.complete", "ccw.debug.serverrepl" ]
 * }
 * </code></pre>
 *
 * <em>Note:</em> You have to specify the nrepl version to use
 * manually. Eg. by using the <code>development</code> configuration or
 * as part of your application.
 *
 * <h2>Caveats</h2>
 * <ul>
 *   <li>The server keeps running in the current console.
 *     Currently there is no way to background the process.</li>
 *   <li>Parallel builds usually work. Unless the <code>build.gradle</code>
 *     is touched between the runs. Then the repl has to be stopped
 *     and restarted afresh to allow again parallel builds.</li>
 * </ul>
 */
class ClojureRepl extends DefaultTask {
    /**
     * Classpath required to generate the documentation.
     */
    @InputFiles
    @Delayed
    def classpath

    /**
     * Adds the given files to classpath. <code>fs</code> is subject
     * to expansion via <code>project.files</code>.
     *
     * @param  fs   Files/directories to add to the classpath
     * @return      Returns <code>this</code>.
     */
    def classpath(Object... fs) {
        classpath = this.getClasspath().plus(project.files(fs))
        this
    }

    /**
     * A <code>Closure</code> configuring the underlying exec spec.
     * This may be used to set eg. heap sizes etc.
     */
    @Delayed
    def jvmOptions

    /**
     * The port for the repl server to listen on. A string or integer.
     */
    def port

    /**
     * The fully qualified name of the repl handler.
     */
    def handler

    /**
     * A list of fully qualified names of middlewares. <em>Note:</em>
     * unused in case a custom <code>handler</code> is set.
     */
    def middleware = []

    /**
     * A list of namespaces which need to be reqired to initialise
     * the repl environment.
     */
    def injections = []

    @TaskAction
    void startRepl() {
        def options = [
            port:    port,
            handler: handler,
            middleware: middleware,
            injections: injections
        ]

        def runtime = [
            "clojuresque/util.clj",
            "clojuresque/tasks/repl.clj"
        ].collect { this.class.classLoader.getResourceAsStream(it) }

        project.javaexec {
            setMain("clojure.main")
            args('-')
            ConfigureUtil.configure delegate, this.getJvmOptions()
            classpath = this.getClasspath()
            standardInput = Util.toInputStream([
                runtime,
                "(clojuresque.tasks.repl/start-repl)",
                Util.optionsToStream(options)
            ])
        }
    }
}
