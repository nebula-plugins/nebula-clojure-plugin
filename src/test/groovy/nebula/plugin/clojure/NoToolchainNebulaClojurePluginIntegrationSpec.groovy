package nebula.plugin.clojure

class NoToolchainNebulaClojurePluginIntegrationSpec extends BaseIntegrationTestKitSpec {

    private final APP_CLJ = '''\
            (ns test.nebula.app)
            
            (defn hello
              [name]
              (println "hello" name))
            '''.stripIndent()


    def 'compiler worker shuts down agents after #mode with failure=#failure'() {
        given:
        def sourceDir = new File(projectDir, 'worker-source')
        sourceDir.mkdirs()
        def source = new File(sourceDir, 'worker.clj')
        def completed = new File(projectDir, 'agent-completed')
        source.text = '''
            (ns worker)
            (def work (agent nil))
            ;; Still running when compilation ends; shutdown-agents must let it finish.
            (send-off work (fn [_] (Thread/sleep 500) (spit "COMPLETED" "done") nil))
        '''.stripIndent().replace('COMPLETED', completed.absolutePath.replace('\\', '/')) +
                (failure ? '\n(throw (ex-info "original compiler failure" {}))\n' : '')
        def classes = new File(projectDir, 'worker-classes')
        classes.mkdirs()
        def output = new File(projectDir, 'worker-output')
        def clojureJar = new File(clojure.lang.RT.protectionDomain.codeSource.location.toURI())
        // Use the real compiler resources and stdin protocol, but never load them in this JVM.
        def process = new ProcessBuilder(
                new File(System.getProperty('java.home'), 'bin/java').absolutePath,
                "-Dclojure.compile.path=${classes.absolutePath}".toString(),
                '-cp', [System.getProperty('java.class.path'), clojureJar, sourceDir].join(File.pathSeparator),
                'clojure.main', '-').redirectErrorStream(true).redirectOutput(output).start()
        process.outputStream.withWriter('UTF-8') { writer ->
            ['clojuresque/util.clj', 'clojuresque/tasks/compile.clj'].each {
                writer << getClass().classLoader.getResource(it).text << '\n'
            }
            // Catch outside main so a compiler error cannot hide missing cleanup via clojure.main's exit.
            writer << '''
                (try
                  (println "TASK-RESULT" (clojuresque.tasks.compile/main))
                  (catch Throwable t
                    (println "COMPILER-ERROR" (.getMessage t))
                    (loop [cause t]
                      (if-let [nested (.getCause cause)]
                        (recur nested)
                        (println "ROOT-CAUSE" (.getMessage cause))))))
            '''.stripIndent()
            writer << "\n{:compile-mode \"${mode}\" :source-files [\"${source.absolutePath.replace('\\', '/')}\"]}\n"
        }

        when:
        def exited = process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)

        then:
        assert exited: "Compiler worker did not exit promptly: ${output.text}"
        process.exitValue() == 0
        assert completed.exists(): 'Shutdown interrupted or discarded the running agent action'
        completed.text == 'done'
        failure ? output.text.contains('ROOT-CAUSE original compiler failure') : output.text.contains('TASK-RESULT true')
        !failure || !output.text.contains('TASK-RESULT')

        cleanup:
        if (process?.isAlive()) {
            process.destroyForcibly()
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        }

        where:
        mode      | failure
        'compile' | false
        'require' | false
        'compile' | true
        'require' | true
    }

    def 'can compile clojure'() {
        buildFile << '''\
            plugins {
                id 'com.netflix.nebula.clojure'
            }
            
            repositories { mavenCentral() }

            dependencies {
                implementation 'org.clojure:clojure:1.10.3'
            }
            '''.stripIndent()

        settingsFile << 'rootProject.name="can-compile-clojure"'

        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ

        when:
        def result = runTasks('build','-s')

        then:
        noExceptionThrown()

        and:
        new File(projectDir, "//build/classes/java/main/test/nebula/app.clj").exists()
    }

    def 'can compile clojure when non-clojure files are present in the source directory'() {
        buildFile << '''\
            plugins {
                id 'com.netflix.nebula.clojure'
            }

            repositories { mavenCentral() }

            dependencies {
                implementation 'org.clojure:clojure:1.10.3'
            }
            '''.stripIndent()

        settingsFile << 'rootProject.name="can-compile-clojure-with-readme"'

        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ
        new File(clojurefiles, 'README.md').text = '# Not a clojure file'

        when:
        def result = runTasks('build', '-s')

        then:
        noExceptionThrown()

        and:
        new File(projectDir, "//build/classes/java/main/test/nebula/app.clj").exists()
    }

    def 'can compile clojure with aotCompile'() {
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
                id 'com.netflix.nebula.clojure'
            }
            
            repositories { mavenCentral() }
            
            clojure.warnOnReflection = true

            dependencies {
                implementation 'org.clojure:clojure:1.10.3'
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

    def 'can compile clojure when source/target compatibility are set'() {
        buildFile << '''\
            plugins {
                id 'com.netflix.nebula.clojure'
            }
            
            repositories { mavenCentral() }

            dependencies {
                implementation 'org.clojure:clojure:1.10.3'
            }
            
            java {
                sourceCompatibility = 1.8
                targetCompatibility = 1.8 
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
}
