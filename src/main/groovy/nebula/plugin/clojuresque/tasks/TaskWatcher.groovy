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

import org.gradle.api.DefaultTask
import org.gradle.api.UnknownTaskException
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Execute a set of tasks in a regular interval. This can be useful when
 * to generate eg. a new set of assets during web development. Or run
 * tests continually during development.
 *
 * <strong>Note:</strong> This is somewhat hacky. Expect problems.
 *
 * @author Meikel Brandmeyer &lt;mb@kotka.de&gt;
 */
class TaskWatcher extends DefaultTask {
    /**
     * The sleeping interval between task runs. The time is measured in
     * milli seconds. Default: 5s.
     */
    @Internal
    def pollingInterval = 5000

    /**
     * Get the tasks, watched by this watcher task.
     */
    @Internal
    def getTasks() {
        watcherTask().tasks
    }

    /**
     * Set the tasks, watched by this watcher task. The argument must be
     * suitable for the <code>tasks</code> property of
     * <code>GradleBuild</code>.
     *
     * @param  tasks The tasks to watch
     * @return       <code>this</code>
     */
    def setTasks(tasks) {
        watcherTask().tasks = tasks
        this
    }

    /**
     * Add a task to be watched by this watcher task. The argument must
     * be suitable for the <code>tasks</code> property of
     * <code>GradleBuild</code>.
     *
     * @param  t The task to watch
     * @return   <code>this</code>
     */
    def task(t) {
        watcherTask().tasks << t
        this
    }

    /**
     * The task action. Call only if you know what you are doing.
     */
    @TaskAction
    def void watch() {
        def t = watcherTask()

        if (t.tasks.size() == 0) {
            logger.info "No tasks to watch. Bailing out."
            return
        }

        logger.info "Watching the following tasks (with ${pollingInterval}ms interval):"
        t.tasks.each { logger.info " * ${it}" }

        while (true) {
            Thread.sleep(pollingInterval)
            logger.info "Polling..."
            t.actions.each { it.execute(t) }
        }
    }

    def private watcherTask() {
        try {
            project.tasks["watchedTaskExecutor"]
        } catch (UnknownTaskException exc) {
            project.task("watchedTaskExecutor", type: GradleBuild) {
                description = "Internal task for TaskWatcher"
                group = "other"
            }
        }
    }
}
