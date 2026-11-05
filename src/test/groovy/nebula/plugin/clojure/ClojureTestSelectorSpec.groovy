package nebula.plugin.clojure

import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import spock.lang.Unroll

/** Functional coverage for test-selector ownership and task input tracking. */
class ClojureTestSelectorSpec extends BaseIntegrationTestKitSpec {
    // Gradle 8.0 supports Java 8–19; CI exercises it on Java 17.
    private static final List<String> VERSIONS =
        ((JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_20) ? [] : ['8.0']) +
            [GradleVersion.current().version]).unique()
    private static final String KEY = 'clojuresque.test.vars'
    private static final String FIRST = 'selector.app-test/first-test'
    private static final String SECOND = 'selector.app-test/second-test'

    @Unroll
    def 'selector changes execute real tests in #topology on Gradle #version, isolation=#isolated'() {
        given:
        def participants = fixture(topology)

        when: 'no selector runs every test, even in child projects'
        def initial = execute(version, isolated, participants)
        def initialReuse = execute(version, isolated, participants)

        then:
        assertStored(initial)
        assertSelected(initial, participants, ['first', 'second'])
        assertReused(initialReuse)
        assertOutcomes(initialReuse, participants, TaskOutcome.UP_TO_DATE)

        when:
        def first = execute(version, isolated, participants, ["-P$KEY=$FIRST"])
        def firstReuse = execute(version, isolated, participants, ["-P$KEY=$FIRST"])

        then:
        assertStored(first)
        assertOutcomes(first, participants, TaskOutcome.SUCCESS)
        assertSelected(first, participants, ['first'])
        assertReused(firstReuse)
        assertOutcomes(firstReuse, participants, TaskOutcome.UP_TO_DATE)

        when: 'a changed selector invalidates configuration and task inputs'
        def second = execute(version, isolated, participants, ["-P$KEY=$SECOND"])
        def secondReuse = execute(version, isolated, participants, ["-P$KEY=$SECOND"])

        then:
        assertStored(second)
        assertOutcomes(second, participants, TaskOutcome.SUCCESS)
        assertSelected(second, participants, ['second'])
        assertReused(secondReuse)
        assertOutcomes(secondReuse, participants, TaskOutcome.UP_TO_DATE)

        when: 'editing selected test source reuses configuration but executes that project'
        def leaf = participants.last()
        def source = new File(projectDir, relative(leaf, 'src/test/clojure/selector/app_test.clj'))
        source.text = source.text.replace('selected-second', 'selected-second-edited')
        def edited = execute(version, isolated, participants, ["-P$KEY=$SECOND"])
        def editedReuse = execute(version, isolated, participants, ["-P$KEY=$SECOND"])

        then:
        assertReused(edited)
        edited.task(taskPath(leaf)).outcome == TaskOutcome.SUCCESS
        (participants - leaf).every { edited.task(taskPath(it)).outcome == TaskOutcome.UP_TO_DATE }
        edited.output.contains("$leaf:selected-second-edited")
        edited.output.contains('Ran 1 tests containing 1 assertions.')
        assertReused(editedReuse)
        assertOutcomes(editedReuse, participants, TaskOutcome.UP_TO_DATE)

        where:
        [version, isolated, topology] << [VERSIONS, [false, true], ['single', 'siblings', 'nested']].combinations()
    }

    @Unroll
    def 'parent-only extras are ignored and local extras win in #topology on Gradle #version, isolation=#isolated'() {
        given:
        def participants = fixture(topology)
        buildFile << "ext.set('$KEY', '$FIRST')\n"
        if (topology == 'nested') {
            write('group/build.gradle', "ext.set('$KEY', '$SECOND')\n")
        }

        when: 'neither root nor immediate-parent extras become child selectors'
        def initial = execute(version, isolated, participants)
        def initialReuse = execute(version, isolated, participants)

        then:
        assertStored(initial)
        assertSelected(initial, participants, ['first', 'second'])
        assertReused(initialReuse)
        assertOutcomes(initialReuse, participants, TaskOutcome.UP_TO_DATE)

        when: 'each child owns its extra, which takes precedence over -P'
        participants.eachWithIndex { path, index ->
            new File(projectDir, relative(path, 'build.gradle')) <<
                "ext.set('$KEY', '${index == 0 ? FIRST : SECOND}')\n"
        }
        def local = execute(version, isolated, participants, ["-P$KEY=$FIRST,$SECOND"])
        def localReuse = execute(version, isolated, participants, ["-P$KEY=$FIRST,$SECOND"])

        then:
        assertStored(local)
        assertOutcomes(local, participants, TaskOutcome.SUCCESS)
        participants.eachWithIndex { path, index ->
            assert local.output.contains("$path:selected-${index == 0 ? 'first' : 'second'}")
            assert !local.output.contains("$path:selected-${index == 0 ? 'second' : 'first'}")
        }
        local.output.count('Ran 1 tests containing 1 assertions.') == participants.size()
        assertReused(localReuse)
        assertOutcomes(localReuse, participants, TaskOutcome.UP_TO_DATE)

        where:
        [version, isolated, topology] << [VERSIONS, [false, true], ['siblings', 'nested']].combinations()
    }

    @Unroll
    def 'selector precedence and timing: #scenario, isolation=#isolated'() {
        given:
        fixture('single', configuration.beforeApply ?: '', configuration.afterApply ?: '')
        if (configuration.gradleProperty != null) {
            write('gradle.properties', "$KEY=${configuration.gradleProperty}\n")
        }

        when:
        def first = execute(GradleVersion.current().version, isolated, [':'], configuration.arguments ?: [])
        def second = execute(GradleVersion.current().version, isolated, [':'], configuration.arguments ?: [])

        then:
        assertStored(first)
        assertOutcomes(first, [':'], TaskOutcome.SUCCESS)
        assertSelected(first, [':'], expected)
        assertReused(second)
        assertOutcomes(second, [':'], TaskOutcome.UP_TO_DATE)

        where:
        scenario                              | expected            | configuration
        'absent selector retains empty list'   | ['first', 'second'] | [afterApply: "assert tasks.named('clojureTest').get().tests.get() == []"]
        'Gradle property'                     | ['first']           | [gradleProperty: FIRST]
        'CLI overrides gradle.properties'     | ['second']          | [gradleProperty: FIRST, arguments: ["-P$KEY=$SECOND"]]
        'extra before apply overrides CLI'    | ['first']           | [beforeApply: "ext.set('$KEY', '$FIRST')", arguments: ["-P$KEY=$SECOND"]]
        'extra after apply overrides CLI'     | ['first']           | [afterApply: "ext.set('$KEY', '$FIRST')", arguments: ["-P$KEY=$SECOND"]]
        'last extra before realization'       | ['second']          | [afterApply: "ext.set('$KEY', '$FIRST'); ext.set('$KEY', '$SECOND')"]
        'extra after realization is too late' | ['first']           | [afterApply: "ext.set('$KEY', '$FIRST'); tasks.named('clojureTest').get(); ext.set('$KEY', '$SECOND')"]
        'explicit task override'              | ['second']          | [afterApply: "ext.set('$KEY', '$FIRST'); tasks.named('clojureTest') { tests.set(['$SECOND']) }", arguments: ["-P$KEY=$FIRST"]]
        'explicit override after realization' | ['second']          | [afterApply: "tasks.named('clojureTest').get().tests.set(['$SECOND'])", arguments: ["-P$KEY=$FIRST"]]
        'explicit empty task selection'       | ['first', 'second'] | [afterApply: "tasks.named('clojureTest') { tests.set([]) }", arguments: ["-P$KEY=$FIRST"]]
        'explicit absent task selection'      | ['first', 'second'] | [afterApply: "tasks.named('clojureTest') { tests.set(providers.gradleProperty('absent.selector').map { it.split(',').toList() }) }", arguments: ["-P$KEY=$FIRST"]]
        'comma splitting with trailing comma' | ['first', 'second'] | [arguments: ["-P$KEY=$FIRST,$SECOND,"]]
        'empty CLI selection'                 | ['first', 'second'] | [arguments: ["-P$KEY="]]
        'empty local extra overrides CLI'     | ['first', 'second'] | [afterApply: "ext.set('$KEY', '')", arguments: ["-P$KEY=$FIRST"]]

        combined:
        isolated << [false, true]
    }

    @Unroll
    def 'changing #source invalidates selection, isolation=#isolated'() {
        given:
        fixture('single')
        if (source == 'task.tests') {
            buildFile << "tasks.named('clojureTest') { tests.set(providers.gradleProperty('selection').map { it.split(',').toList() }) }\n"
        }
        configureSelector(source, FIRST)

        when:
        def first = execute(GradleVersion.current().version, isolated, [':'])
        def firstReuse = execute(GradleVersion.current().version, isolated, [':'])

        then:
        assertStored(first)
        assertSelected(first, [':'], ['first'])
        assertReused(firstReuse)
        assertOutcomes(firstReuse, [':'], TaskOutcome.UP_TO_DATE)

        when:
        configureSelector(source, SECOND)
        def changed = execute(GradleVersion.current().version, isolated, [':'])
        def changedReuse = execute(GradleVersion.current().version, isolated, [':'])

        then:
        assertStored(changed)
        assertOutcomes(changed, [':'], TaskOutcome.SUCCESS)
        assertSelected(changed, [':'], ['second'])
        assertReused(changedReuse)
        assertOutcomes(changedReuse, [':'], TaskOutcome.UP_TO_DATE)

        where:
        [isolated, source] << [[false, true], ['gradle.properties', 'local extra', 'task.tests']].combinations()
    }

    private void configureSelector(String source, String selector) {
        if (source == 'local extra') {
            buildFile << "ext.set('$KEY', '$selector')\n"
        } else {
            write('gradle.properties', "${source == 'task.tests' ? 'selection' : KEY}=$selector\n")
        }
    }

    @Unroll
    def 'a selected failure is executed on Gradle #version, isolation=#isolated'() {
        given:
        fixture('single')
        def source = new File(projectDir, 'src/test/clojure/selector/app_test.clj')
        source << '''
            (deftest failing-test
              (println "selected-failure")
              (is (= 1 2)))
        '''.stripIndent()

        when:
        def first = execute(version, isolated, [':'], ["-P$KEY=$FIRST"])
        def failed = execute(version, isolated, [':'], ["-P$KEY=selector.app-test/failing-test"], true)

        then:
        assertStored(first)
        assertSelected(first, [':'], ['first'])
        failed.task(':clojureTest').outcome == TaskOutcome.FAILED
        failed.output.contains('selected-failure')
        failed.output.contains('FAIL in (failing-test)')
        failed.output.contains('1 failures, 0 errors.')
        assertStored(failed)

        when:
        def passing = execute(version, isolated, [':'], ["-P$KEY=$SECOND"])
        def passingReuse = execute(version, isolated, [':'], ["-P$KEY=$SECOND"])

        then:
        assertOutcomes(passing, [':'], TaskOutcome.SUCCESS)
        assertSelected(passing, [':'], ['second'])
        assertReused(passingReuse)
        assertOutcomes(passingReuse, [':'], TaskOutcome.UP_TO_DATE)

        where:
        [version, isolated] << [VERSIONS, [false, true]].combinations()
    }

    private List<String> fixture(String topology, String beforeApply = '', String customization = '') {
        def participants = topology == 'single' ? [':'] :
            (topology == 'siblings' ? [':library', ':app'] : [':library', ':group:app'])
        settingsFile.text = "rootProject.name = 'selector'\n"
        if (topology != 'single') {
            settingsFile << "include ${participants.collect { "'${it.substring(1)}'" }.join(', ')}\n"
            buildFile.text = ''
            if (topology == 'nested') write('group/build.gradle', '')
        }
        participants.each { path ->
            write(relative(path, 'build.gradle'), """
                plugins { id 'com.netflix.nebula.clojure' apply false }
                $beforeApply
                apply plugin: 'com.netflix.nebula.clojure'
                repositories { mavenCentral() }
                dependencies { implementation 'org.clojure:clojure:1.10.3' }
                $customization
            """.stripIndent())
            write(relative(path, 'src/test/clojure/selector/app_test.clj'), """
                (ns selector.app-test (:require [clojure.test :refer :all]))
                (deftest first-test (println "$path:selected-first") (is (= 2 (+ 1 1))))
                (deftest second-test (println "$path:selected-second") (is (= 4 (+ 2 2))))
            """.stripIndent())
        }
        participants
    }

    private BuildResult execute(String version, boolean isolated, List<String> participants,
                                List<String> options = [], boolean fails = false) {
        def isolationProperty = GradleVersion.version(version) >= GradleVersion.version('9.7') ?
            'org.gradle.isolated-projects' : 'org.gradle.unsafe.isolated-projects'
        def arguments = participants.collect { taskPath(it) } + options +
            ['--configuration-cache', '--configuration-cache-problems=fail', '--no-build-cache',
             "-D$isolationProperty=$isolated", '--stacktrace', '--console=plain', '--max-workers=2']
        // Each test/mode gets a separate fixture, so isolation always starts with a cold cache.
        def runner = GradleRunner.create().withProjectDir(projectDir).withPluginClasspath()
            .withGradleVersion(version).withArguments(arguments.collect { it.toString() })
        fails ? runner.buildAndFail() : runner.build()
    }

    private static void assertStored(BuildResult result) {
        assert result.output.contains('Configuration cache entry stored')
        assert !result.output.contains('Configuration cache problems found')
        assert !result.output.contains('Reusing configuration cache.')
    }

    private static void assertReused(BuildResult result) {
        assert result.output.contains('Configuration cache entry reused')
        assert !result.output.contains('Configuration cache problems found')
    }

    private static void assertOutcomes(BuildResult result, List<String> participants, TaskOutcome outcome) {
        participants.each { assert result.task(taskPath(it)).outcome == outcome }
    }

    private static void assertSelected(BuildResult result, List<String> participants, List<String> expected) {
        participants.each { path ->
            ['first', 'second'].each { test ->
                assert result.output.contains("$path:selected-$test") == expected.contains(test)
            }
        }
        assert result.output.count("Ran ${expected.size()} tests containing ${expected.size()} assertions.") == participants.size()
    }

    private void write(String path, String content) {
        def target = new File(projectDir, path)
        target.parentFile.mkdirs()
        target.text = content
    }

    private static String relative(String projectPath, String path) {
        (projectPath == ':' ? '' : projectPath.substring(1).replace(':', '/') + '/') + path
    }

    private static String taskPath(String projectPath) {
        (projectPath == ':' ? '' : projectPath) + ':clojureTest'
    }
}
