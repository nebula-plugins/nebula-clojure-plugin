package nebula.plugin.clojure

import nebula.test.IntegrationTestKitSpec

class NebulaClojurePluginIntegrationSpec extends IntegrationTestKitSpec {
    def 'can compile clojure'() {
        buildFile << '''\
            plugins {
                id 'nebula.clojure'
            }
            
            repositories { jcenter() }
    
            dependencies {
                implementation 'org.clojure:clojure:1.8.0'
            }
            '''.stripIndent()

        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = '''\
            (ns test.nebula.app)
            
            (defn hello
              [name]
              (println "hello" name))
            '''.stripIndent()

        when:
        def result = runTasks('build')

        then:
        noExceptionThrown()
    }
}
