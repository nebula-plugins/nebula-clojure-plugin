/**
 *
 *  Copyright 2021 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package nebula.plugin.clojure

import nebula.test.IntegrationTestKitSpec
import spock.lang.Unroll

class TestFilteringSpec extends IntegrationTestKitSpec {
    private final APP_CLJ = '''\
            (ns test.nebula.app)
            
            (defn hello
              [name]
              (println "hello" name))
            '''.stripIndent()

    private final TEST1_SUCCESSFUL_CLJ = '''\
            (ns test.nebula.test1
              (:require [clojure.test :refer [deftest is]]))
            (deftest passing-test
              (is (= 4 (+ 2 2))))
            (deftest other-passing-test
              (is (= 6 (+ 3 3))))
            '''.stripIndent()

    private final TEST2_SUCCESSFUL_CLJ = '''
            (ns test.nebula.test2
              (:require [clojure.test :refer [deftest is]]))
            (deftest passing-test
              (is (= 8 (+ 4 4))))
            '''.stripIndent()

    private final TEST3_FAILING_CLJ = '''
            (ns test.nebula.test3
              (:require [clojure.test :refer [deftest is]]))
            (deftest failing-test
              (is (= 5 (+ 2 2))))
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

        def clojurefiles = new File(projectDir, 'src/main/clojure/test/nebula')
        clojurefiles.mkdirs()
        new File(clojurefiles, 'app.clj').text = APP_CLJ

        def testClojurefiles = new File(projectDir, 'src/test/clojure/test/nebula')
        testClojurefiles.mkdirs()

        new File(testClojurefiles, 'test1.clj').text = TEST1_SUCCESSFUL_CLJ
        new File(testClojurefiles, 'test2.clj').text = TEST2_SUCCESSFUL_CLJ
        System.setProperty("ignoreDeprecations", "true")
    }

    def cleanup() {
        System.clearProperty("ignoreDeprecations")
    }

    @Unroll
    def 'all tests run by default - #task task'() {
        when:
        def result = runTasks(task)

        then:
        result.output.contains("Testing test.nebula.test1")
        result.output.contains("Testing test.nebula.test2")
        result.output.contains("Ran 3 tests containing 3 assertions.")

        where:
        task << ['test', 'clojureTest']
    }

    @Unroll
    def 'test filtering works with a project property - filter #filter'() {
        given:
        def testClojurefiles = new File(projectDir, 'src/test/clojure/test/nebula')
        new File(testClojurefiles, 'test3.clj').text = TEST3_FAILING_CLJ

        when:
        def result = runTasks('test', "-Pclojuresque.test.vars=$filter")

        then:
        result.output.contains("Testing test.nebula.test1")
        !result.output.contains("Testing test.nebula.test3")
        result.output.contains("Ran $howMany tests containing $howMany assertions.")

        where:
        description                       | howMany | filter
        'one test'                        | 1       | 'test.nebula.test1/passing-test'
        'other test'                      | 1       | 'test.nebula.test1/other-passing-test'
        'multiple tests'                  | 2       | 'test.nebula.test1/passing-test,test.nebula.test1/other-passing-test'
        'multiple tests, different files' | 3       | 'test.nebula.test1/passing-test,test.nebula.test1/other-passing-test,test.nebula.test2/passing-test'
    }

    def 'test filtering works with clojure and java project - run only clojure test subset using task clojureTest and a property'() {
        given:
        addJavaProjectSetup()

        when:
        def result = runTasks('clojureTest', '-Pclojuresque.test.vars=test.nebula.test1/passing-test') // use clojureTest task for smallest scope

        then:
        result.output.contains("Testing test.nebula.test1")
        !result.output.contains("Testing test.nebula.test2")
        !result.output.contains('nebula.HelloWorldTest')
    }

    def 'test filtering works with clojure and java project - run only java test subset with --tests flag'() {
        given:
        addJavaProjectSetup()

        when:
        def result = runTasks('test', '--tests', 'HelloWorldTest')

        then:
        result.output.contains('nebula.HelloWorldTest > doesSomething PASSED')
        result.output.contains('> Task :clojureTest SKIPPED')
    }

    def 'clojure and java project - all tests run by default - test task'() {
        given:
        addJavaProjectSetup()

        when:
        def result = runTasks('test')

        then:
        result.output.contains("Testing test.nebula.test1")
        result.output.contains("Testing test.nebula.test2")
        result.output.contains('nebula.HelloWorldTest > doesSomething PASSED')
    }

    private void addJavaProjectSetup() {
        buildFile << '''\
            apply plugin: 'java'
            dependencies {
                testImplementation 'junit:junit:4.12'
            }
            import org.gradle.api.tasks.testing.logging.TestLogEvent
            tasks.withType(Test) {
                testLogging {
                    events TestLogEvent.PASSED, TestLogEvent.FAILED
                }
            }
            '''.stripIndent()
        writeUnitTest()
    }
}

