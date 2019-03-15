package nebula.plugin.clojuresque.tasks

import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.model.ObjectFactory

class DefaultClojureSourceSet implements ClojureSourceSet {
    private final SourceDirectorySet clojure

    DefaultClojureSourceSet(String displayName, ObjectFactory objects) {
        this.clojure = objects.sourceDirectorySet("clojure", displayName + " Clojure source")
        this.clojure.getFilter().include("**/*.clj", "**/*.cljs", "**/*.cljc")
    }

    @Override
    SourceDirectorySet getClojure() {
        return clojure
    }
}
