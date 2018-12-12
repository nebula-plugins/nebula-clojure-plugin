package nebula.plugin.clojuresque.tasks

import org.gradle.api.file.SourceDirectorySet

interface ClojureSourceSet {
    SourceDirectorySet getClojure()
}
