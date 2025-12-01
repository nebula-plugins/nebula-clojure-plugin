package nebula.plugin.clojuresque.tasks

import nebula.plugin.clojuresque.Util
import nebula.plugin.utils.tasks.ConfigureUtil
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject;

@DisableCachingByDefault
abstract class ClojureRun extends ClojureSourceTask {

    @InputFiles
    @Classpath
    abstract ConfigurableFileCollection getClasspath()

    @Input
    @Optional
    abstract Property<String> getFn()

    @Option(option = "fn", description = "The clojure function (and optional args) to execute.")
    public void setFn(String fn) {
        this.getFn().set(fn)
    }

    private final ExecOperations execOperations

    private final ObjectFactory objects

    @Internal
    Closure jvmOptions = {}

    @Inject
    ClojureRun(ExecOperations execOperations, ObjectFactory objects) {
        this.execOperations = execOperations
        this.objects = objects
    }

    void jvmOptions(Closure closure) {
        this.jvmOptions = closure
    }

    // Example usage: ./gradlew clojureRun --fn='my-ns/my-fn arg1 arg2'
    @TaskAction
    void run() {

        def options = [
            fn: fn.getOrNull()
        ]

        def runtime = [
            "clojuresque/util.clj",
            "clojuresque/tasks/run.clj"
        ].collect { owner.class.classLoader.getResourceAsStream it }

        def objectFactory = objects
        execOperations.javaexec {
            setMainClass("clojure.main")
            args('-')
            ConfigureUtil.configure delegate, jvmOptions
            classpath = objectFactory.fileCollection().from(
                this.srcDirs,
                this.classpath
            )
            standardInput = Util.toInputStream([
                runtime,
                "(clojuresque.tasks.run/main)",
                Util.optionsToStream(options)
            ])
            if(launcher.isPresent()) {
                setExecutable(launcher.get().getExecutablePath().getAsFile().getAbsolutePath())
            }
        }
    }
}
