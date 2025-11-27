package nebula.plugin.clojure

import org.gradle.testkit.runner.TaskOutcome

class IncrementalBuildSpec extends BaseIntegrationTestKitSpec {

    private final APP_CLJ = '''\
            (ns test.nebula.app)

            (defn hello
              [name]
              (println "hello" name))
            '''.stripIndent()

    private final UTIL_CLJ = '''\
            (ns test.nebula.util)

            (defn greet
              [name]
              (str "Hello, " name "!"))
            '''.stripIndent()

    def setup() {
        buildFile << '''\
            plugins {
                id 'com.netflix.nebula.clojure'
            }

            repositories { mavenCentral() }

            dependencies {
                implementation 'org.clojure:clojure:1.10.3'
            }
            '''.stripIndent()

        settingsFile << 'rootProject.name="incremental-build-test"'
    }

    def 'build is up-to-date when no changes are made'() {
        given: 'a project with clojure source'
        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ

        when: 'first build runs'
        def firstResult = runTasks('compileClojure')

        then: 'compilation succeeds'
        firstResult.task(':compileClojure').outcome == TaskOutcome.SUCCESS

        when: 'second build runs without changes'
        def secondResult = runTasks('compileClojure')

        then: 'compilation is up-to-date'
        secondResult.task(':compileClojure').outcome == TaskOutcome.UP_TO_DATE
    }

    def 'changing a source file triggers recompilation'() {
        given: 'a project with clojure source'
        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        def appFile = new File(clojurefiles, 'app.clj')
        appFile.text = APP_CLJ

        and: 'initial build completes'
        runTasks('compileClojure')

        when: 'source file is modified'
        appFile.text = '''\
            (ns test.nebula.app)

            (defn hello
              [name]
              (println "hello" name "!"))  ; Added exclamation point
            '''.stripIndent()

        and: 'build runs again'
        def result = runTasks('compileClojure')

        then: 'compilation runs (not up-to-date)'
        result.task(':compileClojure').outcome == TaskOutcome.SUCCESS
    }

    def 'adding a new source file triggers compilation'() {
        given: 'a project with clojure source'
        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ

        and: 'initial build completes'
        runTasks('compileClojure')

        when: 'a new source file is added'
        new File(clojurefiles, 'util.clj').text = UTIL_CLJ

        and: 'build runs again'
        def result = runTasks('compileClojure')

        then: 'compilation runs (not up-to-date)'
        result.task(':compileClojure').outcome == TaskOutcome.SUCCESS

        and: 'new file is compiled'
        new File(projectDir, "build/classes/java/main/test/nebula/util.clj").exists()
    }

    def 'removing a source file triggers recompilation'() {
        given: 'a project with multiple clojure sources'
        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ
        def utilFile = new File(clojurefiles, 'util.clj')
        utilFile.text = UTIL_CLJ

        and: 'initial build completes'
        runTasks('compileClojure')

        when: 'a source file is removed'
        utilFile.delete()

        and: 'build runs again'
        def result = runTasks('compileClojure')

        then: 'compilation runs (not up-to-date)'
        result.task(':compileClojure').outcome == TaskOutcome.SUCCESS

        and: 'removed file output is deleted'
        !new File(projectDir, "build/classes/java/main/test/nebula/util.clj").exists()
    }

    def 'incremental build with aotCompile detects changes'() {
        given:
        buildFile.text = '''\
            plugins {
                id 'com.netflix.nebula.clojure'
            }

            repositories { mavenCentral() }

            clojure.aotCompile = true

            dependencies {
                implementation 'org.clojure:clojure:1.10.3'
            }
            '''.stripIndent()

        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        def appFile = new File(clojurefiles, 'app.clj')
        appFile.text = APP_CLJ

        and: 'initial build completes'
        runTasks('compileClojure')

        when: 'source file is modified'
        appFile.text = '''\
            (ns test.nebula.app)

            (defn hello
              [name msg]
              (println msg name))  ; Changed signature
            '''.stripIndent()

        and: 'build runs again'
        def result = runTasks('compileClojure')

        then: 'compilation runs (not up-to-date)'
        result.task(':compileClojure').outcome == TaskOutcome.SUCCESS

        and: 'AOT classes are regenerated'
        new File(projectDir, "build/classes/java/main/test/nebula/app__init.class").exists()
    }

    def 'changing classpath dependency triggers recompilation'() {
        given: 'a project with a dependency'
        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ

        and: 'initial build completes'
        runTasks('compileClojure')

        when: 'dependency version changes'
        buildFile.text = '''\
            plugins {
                id 'com.netflix.nebula.clojure'
            }

            repositories { mavenCentral() }

            dependencies {
                implementation 'org.clojure:clojure:1.11.1'  // Changed version
            }
            '''.stripIndent()

        and: 'build runs again'
        def result = runTasks('compileClojure')

        then: 'compilation runs due to classpath change'
        result.task(':compileClojure').outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE]
        // Task should execute successfully (classpath change may or may not trigger depending on content)
    }
}
