/*-
 * Copyright 2013 © Meikel Brandmeyer.
 * All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package nebula.plugin.utils.tasks

import kotka.gradle.utils.Filterable

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.util.PatternSet

/**
 * A task based on source directories. As such it very similar to
 * gradle's <code>SourceTask</code>. However, you still get access to
 * the underlying directories, while the <code>SourceTask</code> only
 * gives you the source files.
 *
 * The task is <code>Filterable</code> like <code>SourceTask</code>.
 * Please see the documentation there for method documentation. Note:
 * Filtering works on files, not the directories.
 *
 * @author Meikel Brandmeyer &lt;mb@kotka.de&gt;
 */
class SourceDirectoryTask extends DefaultTask {
    @Internal
    def srcDirs = []

    @Internal
    Set getExcludes() {
        return excludes
    }

    @Internal
    Set getIncludes() {
        return includes
    }
    

    /**
     * Get the underlying source directories as
     * <code>FileCollection</code>.
     *
     * @return  The source directories as <code>FileCollection</code>
     */
    def FileCollection getSrcDirs() {
        project.files(
                srcDirs.collect {
                    (it instanceof SourceDirectorySet) ?
                            it.srcDirs :
                            it
                }
        )
    }

    /**
     * Set the source directories. Note: this function replaces the
     * already defined directories! The directories are subject to
     * expansion according to <code>Project.files()</code>.
     *
     * @param  dirs A collection with source directories
     * @return this
     */
    def setSrcDirs(dirs) {
        /* XXX: Groovy bug in closure scoping. */
        def x = srcDirs
        x.clear()
        dirs.each { x << it }
        this
    }

    /**
     * Add a source directory. Note: this function adds a new root to
     * already defined directories! The directories are subject to
     * expansion according to <code>Project.files()</code>.
     *
     * @param  dirs The directory to add
     * @return this
     */
    def srcDir(dir) {
        srcDirs << dir
        this
    }

    /**
     * Add source directories. Note: this function adds a new root to
     * already defined directories! The directories are subject to
     * expansion according to <code>Project.files()</code>.
     *
     * @param  dirs One or more directories to add
     * @return this
     */
    def srcDirs(Object... dirs) {
        /* XXX: Groovy bug in closure scoping. */
        def x = srcDirs
        dirs.each { x << it }
        this
    }

    /**
     * Add a source set to the tasks source. Note: this function adds a
     * new root to already defined directories! The directories are
     * subject to expansion according to <code>Project.files()</code>.
     *
     * @param  sourceSet The source set to add
     * @return this
     */
    def from(SourceDirectorySet sourceSet) {
        srcDirs << sourceSet
        this
    }

    /**
     * Get the tree of source files. The source files are filtered
     * according to the set filters.
     *
     * @return  The source files contained in the source directories
     */
    @InputFiles
    @SkipWhenEmpty
    def FileTree getSource() {
        project.files(srcDirs).asFileTree
    }
}
