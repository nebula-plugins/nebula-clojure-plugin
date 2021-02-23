package nebula.plugin.clojure

import nebula.test.IntegrationTestKitSpec

class NebulaClojurePluginIntegrationSpec extends IntegrationTestKitSpec {

    def setup() {
        debug = true
    }
    private final APP_CLJ = '''\
            (ns test.nebula.app)
            
            (defn hello
              [name]
              (println "hello" name))
            '''.stripIndent()


    def 'can compile clojure'() {
        buildFile << '''\
            plugins {
                id 'nebula.clojure'
            }
            
            repositories { mavenCentral() }
    
            dependencies {
                implementation 'org.clojure:clojure:1.8.0'
            }
            '''.stripIndent()

        settingsFile << 'rootProject.name="can-compile-clojure"'

        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ

        when:
        def result = runTasks('build')

        then:
        noExceptionThrown()

        and:
        new File(projectDir, "//build/classes/java/main/test/nebula/app.clj").exists()
    }

    def 'can compile clojure with aotCompile'() {
        buildFile << '''\
            plugins {
                id 'nebula.clojure'
            }
            
            repositories { mavenCentral() }
            
            clojure.aotCompile = true

            dependencies {
                implementation 'org.clojure:clojure:1.8.0'
            }
            '''.stripIndent()

        settingsFile << 'rootProject.name="can-compile-clojure-aotCompile"'

        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ


        when:
        def result = runTasks('build')

        then:
        noExceptionThrown()

        and:
        new File(projectDir, "//build/classes/java/main/test/nebula/app__init.class").exists()
    }

    def 'can compile clojure with warnOnReflection'() {
        buildFile << '''\
            plugins {
                id 'nebula.clojure'
            }
            
            repositories { mavenCentral() }
            
            clojure.warnOnReflection = true

            dependencies {
                implementation 'org.clojure:clojure:1.8.0'
            }
            '''.stripIndent()

        settingsFile << 'rootProject.name="can-compile-clojure-warnOnReflection"'

        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ

        when:
        def result = runTasks('build')

        then:
        noExceptionThrown()

        and:
        new File(projectDir, "//build/classes/java/main/test/nebula/app.clj").exists()
    }
}
