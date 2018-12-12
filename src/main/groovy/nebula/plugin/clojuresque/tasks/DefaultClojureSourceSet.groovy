package nebula.plugin.clojuresque.tasks

import org.gradle.api.Action
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.model.ObjectFactory

class DefaultClojureSourceSet implements ClojureSourceSet {
    private final SourceDirectorySet clojure;

    DefaultClojureSourceSet(String name, ObjectFactory objects) {
        this.clojure = objects.sourceDirectorySet(name, name);
        this.clojure.getFilter().include("**/*.clj", "**/*.cljs", "**/*.cljc")
    }

    @Override
    SourceDirectorySet getClojure() {
        return clojure
    }

    @Override
    ClojureSourceSet clojure(Action<? super SourceDirectorySet> configureAction) {
        configureAction.execute(clojure)
        return this
    }
}
