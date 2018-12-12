package nebula.plugin.clojuresque.tasks

import org.gradle.api.Action
import org.gradle.api.file.SourceDirectorySet

interface ClojureSourceSet {
    SourceDirectorySet getClojure()

    ClojureSourceSet clojure(Action<? super SourceDirectorySet> configureAction)
}
