package nebula.plugin.clojure

class ClojureRunSpec extends BaseIntegrationTestKitSpec {

    private final APP_CLJ = '''\
            (ns test.nebula.app)

            (defn hello
              [name]
              (println "Hello," name))

            (defn greet-world
              []
              (println "Hello, World!"))
            '''.stripIndent()

    def 'clojureRun task can be created and registered'() {
        given:
        buildFile << '''\
            plugins {
                id 'com.netflix.nebula.clojure'
            }

            repositories { mavenCentral() }

            dependencies {
                implementation 'org.clojure:clojure:1.10.3'
            }
            '''.stripIndent()

        settingsFile << 'rootProject.name="clojure-run-test"'

        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ

        when:
        def result = runTasks('tasks', '--all')

        then:
        noExceptionThrown()
        result.output.contains('clojureRun')
    }

    def 'clojureRun task accepts --fn command line option'() {
        given:
        buildFile << '''\
            plugins {
                id 'com.netflix.nebula.clojure'
            }

            repositories { mavenCentral() }

            dependencies {
                implementation 'org.clojure:clojure:1.10.3'
            }
            '''.stripIndent()

        settingsFile << 'rootProject.name="clojure-run-with-fn"'

        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ

        when:
        def result = runTasks('clojureRun', '--fn=test.nebula.app/greet-world')

        then:
        noExceptionThrown()
        result.output.contains('Hello, World!')
    }

    def 'clojureRun task can be configured with fn property'() {
        given:
        buildFile << '''\
            plugins {
                id 'com.netflix.nebula.clojure'
            }

            repositories { mavenCentral() }

            dependencies {
                implementation 'org.clojure:clojure:1.10.3'
            }

            tasks.named('clojureRun') {
                fn = 'test.nebula.app/greet-world'
            }
            '''.stripIndent()

        settingsFile << 'rootProject.name="clojure-run-configured"'

        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ

        when:
        def result = runTasks('clojureRun')

        then:
        noExceptionThrown()
        result.output.contains('Hello, World!')
    }

    def 'clojureRun task works with fn property using Provider API'() {
        given:
        buildFile << '''\
            plugins {
                id 'com.netflix.nebula.clojure'
            }

            repositories { mavenCentral() }

            dependencies {
                implementation 'org.clojure:clojure:1.10.3'
            }

            tasks.named('clojureRun') {
                fn.set('test.nebula.app/greet-world')
            }
            '''.stripIndent()

        settingsFile << 'rootProject.name="clojure-run-provider-api"'

        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ

        when:
        def result = runTasks('clojureRun')

        then:
        noExceptionThrown()
        result.output.contains('Hello, World!')
    }
}
