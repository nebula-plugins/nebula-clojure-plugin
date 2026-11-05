package nebula.plugin.clojure

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import spock.lang.Unroll

/** Functional coverage for test-selector ownership and task input tracking. */
class ClojureTestSelectorSpec extends BaseIntegrationTestKitSpec {
    // 8.0 is the assessed lower bound of README's 8.x claim, not a new support policy.
    private static final List<String> VERSIONS = ['8.0', GradleVersion.current().version].unique()
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
        stored(initial)
        selected(initial, participants, ['first', 'second'])
        reused(initialReuse)
        outcomes(initialReuse, participants, TaskOutcome.UP_TO_DATE)

        when:
        def first = execute(version, isolated, participants, ["-P$KEY=$FIRST"])
        def firstReuse = execute(version, isolated, participants, ["-P$KEY=$FIRST"])

        then:
        stored(first)
        outcomes(first, participants, TaskOutcome.SUCCESS)
        selected(first, participants, ['first'])
        reused(firstReuse)
        outcomes(firstReuse, participants, TaskOutcome.UP_TO_DATE)

        when: 'a changed selector invalidates configuration and task inputs'
        def second = execute(version, isolated, participants, ["-P$KEY=$SECOND"])
        def secondReuse = execute(version, isolated, participants, ["-P$KEY=$SECOND"])

        then:
        stored(second)
        outcomes(second, participants, TaskOutcome.SUCCESS)
        selected(second, participants, ['second'])
        reused(secondReuse)
        outcomes(secondReuse, participants, TaskOutcome.UP_TO_DATE)

        when: 'editing selected test source reuses configuration but executes that project'
        def leaf = participants.last()
        def source = new File(projectDir, relative(leaf, 'src/test/clojure/selector/app_test.clj'))
        source.text = source.text.replace('selected-second', 'selected-second-edited')
        def edited = execute(version, isolated, participants, ["-P$KEY=$SECOND"])
        def editedReuse = execute(version, isolated, participants, ["-P$KEY=$SECOND"])

        then:
        reused(edited)
        edited.task(taskPath(leaf)).outcome == TaskOutcome.SUCCESS
        (participants - leaf).every { edited.task(taskPath(it)).outcome == TaskOutcome.UP_TO_DATE }
        edited.output.contains("$leaf:selected-second-edited")
        edited.output.contains('Ran 1 tests containing 1 assertions.')
        reused(editedReuse)
        outcomes(editedReuse, participants, TaskOutcome.UP_TO_DATE)

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
        stored(initial)
        selected(initial, participants, ['first', 'second'])
        reused(initialReuse)
        outcomes(initialReuse, participants, TaskOutcome.UP_TO_DATE)

        when: 'each child owns its extra, which takes precedence over -P'
        participants.eachWithIndex { path, index ->
            new File(projectDir, relative(path, 'build.gradle')) <<
                "ext.set('$KEY', '${index == 0 ? FIRST : SECOND}')\n"
        }
        def local = execute(version, isolated, participants, ["-P$KEY=$FIRST,$SECOND"])
        def localReuse = execute(version, isolated, participants, ["-P$KEY=$FIRST,$SECOND"])

        then:
        stored(local)
        outcomes(local, participants, TaskOutcome.SUCCESS)
        participants.eachWithIndex { path, index ->
            assert local.output.contains("$path:selected-${index == 0 ? 'first' : 'second'}")
            assert !local.output.contains("$path:selected-${index == 0 ? 'second' : 'first'}")
        }
        local.output.count('Ran 1 tests containing 1 assertions.') == participants.size()
        reused(localReuse)
        outcomes(localReuse, participants, TaskOutcome.UP_TO_DATE)

        where:
        [version, isolated, topology] << [VERSIONS, [false, true], ['siblings', 'nested']].combinations()
    }

    @Unroll
    def 'selector precedence and timing: #scenario, isolation=#isolated'() {
        given:
        fixture('single', beforeApply, customization)
        if (properties) write('gradle.properties', "$KEY=$FIRST\n")

        when:
        def first = execute(GradleVersion.current().version, isolated, [':'], arguments)
        def second = execute(GradleVersion.current().version, isolated, [':'], arguments)

        then:
        stored(first)
        outcomes(first, [':'], TaskOutcome.SUCCESS)
        selected(first, [':'], expected)
        reused(second)
        outcomes(second, [':'], TaskOutcome.UP_TO_DATE)

        where:
        [isolated, scenario, beforeApply, customization, properties, arguments, expected] <<
            [false, true].collectMany { isolation ->
                [
                    ['absent selector retains empty list', '', "assert tasks.named('clojureTest').get().tests.get() == []", false, [], ['first', 'second']],
                    ['Gradle property', '', '', true, [], ['first']],
                    ['CLI overrides gradle.properties', '', '', true, ["-P$KEY=$SECOND"], ['second']],
                    ['extra before apply overrides CLI', "ext.set('$KEY', '$FIRST')", '', false, ["-P$KEY=$SECOND"], ['first']],
                    ['extra after apply overrides CLI', '', "ext.set('$KEY', '$FIRST')", false, ["-P$KEY=$SECOND"], ['first']],
                    ['last extra before realization', '', "ext.set('$KEY', '$FIRST'); ext.set('$KEY', '$SECOND')", false, [], ['second']],
                    ['extra after realization is too late', '', "ext.set('$KEY', '$FIRST'); tasks.named('clojureTest').get(); ext.set('$KEY', '$SECOND')", false, [], ['first']],
                    ['explicit task override', '', "ext.set('$KEY', '$FIRST'); tasks.named('clojureTest') { tests.set(['$SECOND']) }", false, ["-P$KEY=$FIRST"], ['second']],
                    ['explicit override after realization', '', "tasks.named('clojureTest').get().tests.set(['$SECOND'])", false, ["-P$KEY=$FIRST"], ['second']],
                    ['explicit empty task selection', '', "tasks.named('clojureTest') { tests.set([]) }", false, ["-P$KEY=$FIRST"], ['first', 'second']],
                    ['explicit absent task selection', '', "tasks.named('clojureTest') { tests.set(providers.gradleProperty('absent.selector').map { it.split(',').toList() }) }", false, ["-P$KEY=$FIRST"], ['first', 'second']],
                    ['comma splitting with trailing comma', '', '', false, ["-P$KEY=$FIRST,$SECOND,"], ['first', 'second']],
                    ['empty CLI selection', '', '', false, ["-P$KEY="], ['first', 'second']],
                    ['empty local extra overrides CLI', '', "ext.set('$KEY', '')", false, ["-P$KEY=$FIRST"], ['first', 'second']]
                ].collect { [isolation] + it }
            }
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
        stored(first)
        selected(first, [':'], ['first'])
        reused(firstReuse)
        outcomes(firstReuse, [':'], TaskOutcome.UP_TO_DATE)

        when:
        configureSelector(source, SECOND)
        def changed = execute(GradleVersion.current().version, isolated, [':'])
        def changedReuse = execute(GradleVersion.current().version, isolated, [':'])

        then:
        stored(changed)
        outcomes(changed, [':'], TaskOutcome.SUCCESS)
        selected(changed, [':'], ['second'])
        reused(changedReuse)
        outcomes(changedReuse, [':'], TaskOutcome.UP_TO_DATE)

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
        stored(first)
        selected(first, [':'], ['first'])
        failed.task(':clojureTest').outcome == TaskOutcome.FAILED
        failed.output.contains('selected-failure')
        failed.output.contains('FAIL in (failing-test)')
        failed.output.contains('1 failures, 0 errors.')
        stored(failed)

        when:
        def passing = execute(version, isolated, [':'], ["-P$KEY=$SECOND"])
        def passingReuse = execute(version, isolated, [':'], ["-P$KEY=$SECOND"])

        then:
        outcomes(passing, [':'], TaskOutcome.SUCCESS)
        selected(passing, [':'], ['second'])
        reused(passingReuse)
        outcomes(passingReuse, [':'], TaskOutcome.UP_TO_DATE)

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

    private static boolean stored(BuildResult result) {
        assert result.output.contains('Configuration cache entry stored')
        assert !result.output.contains('Configuration cache problems found')
        assert !result.output.contains('Reusing configuration cache.')
        true
    }

    private static boolean reused(BuildResult result) {
        assert result.output.contains('Configuration cache entry reused')
        assert !result.output.contains('Configuration cache problems found')
        true
    }

    private static boolean outcomes(BuildResult result, List<String> participants, TaskOutcome outcome) {
        participants.each { assert result.task(taskPath(it)).outcome == outcome }
        true
    }

    private static boolean selected(BuildResult result, List<String> participants, List<String> expected) {
        participants.each { path ->
            ['first', 'second'].each { test ->
                assert result.output.contains("$path:selected-$test") == expected.contains(test)
            }
        }
        assert result.output.count("Ran ${expected.size()} tests containing ${expected.size()} assertions.") == participants.size()
        true
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
