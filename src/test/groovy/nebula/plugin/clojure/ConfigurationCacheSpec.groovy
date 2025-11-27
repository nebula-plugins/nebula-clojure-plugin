package nebula.plugin.clojure

class ConfigurationCacheSpec extends BaseIntegrationTestKitSpec {

    private final APP_CLJ = '''\
            (ns test.nebula.app)

            (defn hello
              [name]
              (println "hello" name))
            '''.stripIndent()

    def 'configuration cache is reused on second build'() {
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

        settingsFile << 'rootProject.name="config-cache-test"'

        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ

        when: 'first build runs and stores configuration cache'
        def firstResult = runTasks('build')

        then: 'build succeeds and configuration cache is stored'
        noExceptionThrown()
        firstResult.output.contains('Configuration cache entry stored')

        when: 'second build runs with same configuration'
        def secondResult = runTasks('build')

        then: 'configuration cache is reused'
        noExceptionThrown()
        secondResult.output.contains('Configuration cache entry reused')
    }

    def 'configuration cache works with aotCompile'() {
        given:
        buildFile << '''\
            plugins {
                id 'com.netflix.nebula.clojure'
            }

            repositories { mavenCentral() }

            clojure.aotCompile = true

            dependencies {
                implementation 'org.clojure:clojure:1.10.3'
            }
            '''.stripIndent()

        settingsFile << 'rootProject.name="config-cache-aot-test"'

        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ

        when: 'first build runs'
        def firstResult = runTasks('build')

        then: 'configuration cache is stored'
        firstResult.output.contains('Configuration cache entry stored')

        when: 'second build runs'
        def secondResult = runTasks('build')

        then: 'configuration cache is reused'
        secondResult.output.contains('Configuration cache entry reused')
        and: 'compiled classes exist'
        new File(projectDir, "build/classes/java/main/test/nebula/app__init.class").exists()
    }

    def 'configuration cache works with warnOnReflection'() {
        given:
        buildFile << '''\
            plugins {
                id 'com.netflix.nebula.clojure'
            }

            repositories { mavenCentral() }

            clojure.warnOnReflection = true

            dependencies {
                implementation 'org.clojure:clojure:1.10.3'
            }
            '''.stripIndent()

        settingsFile << 'rootProject.name="config-cache-warn-test"'

        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ

        when: 'first build runs'
        def firstResult = runTasks('build')

        then: 'configuration cache is stored'
        firstResult.output.contains('Configuration cache entry stored')

        when: 'second build runs'
        def secondResult = runTasks('build')

        then: 'configuration cache is reused'
        secondResult.output.contains('Configuration cache entry reused')
    }

    def 'configuration cache works with clojure tests'() {
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

        settingsFile << 'rootProject.name="config-cache-test-run"'

        def mainClojureFiles = new File(projectDir, 'src/main/clojure/test/nebula')
        mainClojureFiles.mkdirs()
        new File(mainClojureFiles, 'app.clj').text = APP_CLJ

        def testClojureFiles = new File(projectDir, 'src/test/clojure/test/nebula')
        testClojureFiles.mkdirs()
        new File(testClojureFiles, 'app_test.clj').text = '''\
            (ns test.nebula.app-test
              (:require [clojure.test :refer :all]
                        [test.nebula.app :as app]))

            (deftest passing-test
              (is (= 1 1)))
            '''.stripIndent()

        when: 'first test run'
        def firstResult = runTasks('test')

        then: 'configuration cache is stored'
        firstResult.output.contains('Configuration cache entry stored')

        when: 'second test run'
        def secondResult = runTasks('test')

        then: 'configuration cache is reused'
        secondResult.output.contains('Configuration cache entry reused')
    }
}
