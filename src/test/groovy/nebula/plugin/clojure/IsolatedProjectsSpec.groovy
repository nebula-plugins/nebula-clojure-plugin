package nebula.plugin.clojure

import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import spock.lang.Unroll

import java.util.jar.JarFile
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/** Strict configuration-cache regressions for locally configured Clojure projects. */
class IsolatedProjectsSpec extends BaseIntegrationTestKitSpec {
    // Match selector coverage: exercise Gradle 8.0 on a compatible JVM, without a new support floor.
    private static final List<String> VERSIONS =
        (System.getProperty('clojure.test.gradleVersions')?.tokenize(',') ?:
            ((JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_20) ? [] : ['8.0']) +
                [GradleVersion.current().version])).unique()
    private static final String FILTER = '-Pclojuresque.test.vars=assessment.app-test/passing'

    @Unroll
    def 'local participation: #topology with Gradle #version, isolation=#isolated'() {
        given:
        def participants = fixture(topology)
        write('gradle.properties', 'assessment.aot=true\n')
        def requested = participants.collectMany { [taskPath(it, 'build'), taskPath(it, 'clojureRun')] }

        when:
        def first = execute(version, isolated, requested + FILTER)

        then:
        stored(first)
        !isolated || first.output.toLowerCase().contains('isolated projects')
        participants.every { path ->
            first.task(taskPath(path, 'compileClojure')).outcome == TaskOutcome.SUCCESS &&
                first.task(taskPath(path, 'clojureTest')).outcome == TaskOutcome.SUCCESS &&
                first.task(taskPath(path, 'clojureRun')).outcome == TaskOutcome.SUCCESS &&
                jarEntries(path).contains(mainResource(path, '__init.class'))
        }
        first.output.contains('Ran 1 tests containing 2 assertions.')
        first.output.contains('app-v1')
        topology == 'single' || first.output.contains('dependency=app-v1')
        !first.output.contains('unselected-test')

        when:
        def second = execute(version, isolated, requested + FILTER)

        then:
        reused(second)
        participants.every { path ->
            second.task(taskPath(path, 'compileClojure')).outcome == TaskOutcome.UP_TO_DATE &&
                second.task(taskPath(path, 'jar')).outcome == TaskOutcome.UP_TO_DATE
        }
        second.output.contains('app-v1') // clojureRun executes even when compilation is up-to-date

        when: 'a task input changes without changing configuration'
        def leaf = participants.last()
        def source = new File(projectDir, relative(leaf, 'src/main/clojure/assessment/app.clj'))
        source.text = source.text.replace('app-v1', 'app-v2')
        def changedSource = execute(version, isolated, requested + FILTER)

        then:
        reused(changedSource)
        changedSource.task(taskPath(leaf, 'compileClojure')).outcome == TaskOutcome.SUCCESS
        changedSource.task(taskPath(leaf, 'jar')).outcome == TaskOutcome.SUCCESS
        changedSource.output.contains('app-v2')

        when: 'a provider-backed extension input changes'
        write('gradle.properties', 'assessment.aot=false\n')
        def changedConfiguration = execute(version, isolated, requested + FILTER)

        then:
        // Newer Gradle versions can re-read provider-backed task inputs without configuring again.
        changedConfiguration.output.contains('Configuration cache entry stored') ||
            changedConfiguration.output.contains('Configuration cache entry reused')
        participants.every { path ->
            changedConfiguration.task(taskPath(path, 'compileClojure')).outcome == TaskOutcome.SUCCESS &&
                jarEntries(path).contains(mainResource(path, '.clj'))
        }
        changedConfiguration.output.contains('app-v2')

        when:
        def finalBuild = execute(version, isolated, requested + FILTER)

        then:
        reused(finalBuild)
        participants.every { finalBuild.task(taskPath(it, 'compileClojure')).outcome == TaskOutcome.UP_TO_DATE }

        where:
        [version, isolated, topology] << [VERSIONS, [false, true], ['single', 'multi', 'nested']].combinations()
    }

    @Unroll
    def 'enabled uberjar tracks archive inputs in #topology on Gradle #version, isolation=#isolated'() {
        given:
        def participants = uberFixture(topology)
        def requested = participants.collect { taskPath(it, 'uberjar') }
        def leaf = participants.last()

        when: 'the opt-in workflow runs without configuration cache'
        def baseline = runner(version, requested + '--no-configuration-cache').build()

        then:
        participants.every { path ->
            baseline.task(taskPath(path, 'uberjar')).outcome == TaskOutcome.SUCCESS &&
                assertUberArchive(path, 'app-v1', 'runtime-v1')
        }

        when: 'cold storage also executes archive creation'
        participants.each { assert uberArchive(it).delete() }
        def first = execute(version, isolated, requested)
        def second = execute(version, isolated, requested)

        then:
        stored(first)
        reused(second)
        participants.every { path ->
            first.task(taskPath(path, 'uberjar')).outcome == TaskOutcome.SUCCESS &&
                second.task(taskPath(path, 'uberjar')).outcome == TaskOutcome.UP_TO_DATE &&
                assertUberArchive(path, 'app-v1', 'runtime-v1')
        }
        topology == 'single' || new JarFile(uberArchive(leaf)).withCloseable {
            it.getEntry('assessment/library.clj') != null
        }

        when: 'the archive is deleted while configuration remains cached'
        assert uberArchive(leaf).delete()
        def regenerated = execute(version, isolated, requested)

        then:
        reused(regenerated)
        regenerated.task(taskPath(leaf, 'uberjar')).outcome == TaskOutcome.SUCCESS
        assertUberArchive(leaf, 'app-v1', 'runtime-v1')

        when: 'application sources change'
        def source = new File(projectDir, relative(leaf, 'src/main/clojure/' + mainResource(leaf, '.clj')))
        source.text = source.text.replace('app-v1', 'app-v2')
        def changedSource = execute(version, isolated, requested)

        then:
        reused(changedSource)
        changedSource.task(taskPath(leaf, 'uberjar')).outcome == TaskOutcome.SUCCESS
        assertUberArchive(leaf, 'app-v2', 'runtime-v1')

        when: 'a runtime archive changes in place without changing configuration'
        writeRuntimeJar(leaf, 'runtime-v2')
        def changedDependency = execute(version, isolated, requested)
        def dependencyReuse = execute(version, isolated, requested)

        then:
        reused(changedDependency)
        changedDependency.task(taskPath(leaf, 'uberjar')).outcome == TaskOutcome.SUCCESS
        changedDependency.task(taskPath(leaf, 'compileClojure')).outcome == TaskOutcome.UP_TO_DATE
        assertUberArchive(leaf, 'app-v2', 'runtime-v2')
        reused(dependencyReuse)
        dependencyReuse.task(taskPath(leaf, 'uberjar')).outcome == TaskOutcome.UP_TO_DATE

        when: 'consumers override archive naming and manifest settings'
        new File(projectDir, relative(leaf, 'build.gradle')) << '''
            tasks.named('uberjar') {
                archiveBaseName.set('custom-archive')
                archiveVersion.set('2.0')
                archiveClassifier.set('bundle')
                manifest { attributes('Updated-Manifest': 'configured') }
            }
        '''.stripIndent()
        def customized = execute(version, isolated, requested)
        def customizedReuse = execute(version, isolated, requested)
        def customJar = new File(projectDir, relative(leaf, 'build/libs/custom-archive-2.0-bundle.jar'))

        then:
        stored(customized)
        customized.task(taskPath(leaf, 'uberjar')).outcome == TaskOutcome.SUCCESS
        assertUberArchive(leaf, 'app-v2', 'runtime-v2', customJar)
        new JarFile(customJar).withCloseable { it.manifest.mainAttributes.getValue('Updated-Manifest') == 'configured' }
        reused(customizedReuse)
        customizedReuse.task(taskPath(leaf, 'uberjar')).outcome == TaskOutcome.UP_TO_DATE

        where:
        [version, isolated, topology] << [VERSIONS, [false, true], ['single', 'multi', 'nested']].combinations()
    }

    @Unroll
    def 'uberjar keeps opt-in and realized task selection on Gradle #version, isolation=#isolated'() {
        given:
        fixture('single')
        buildFile.text = buildFile.text.replace("plugins { id 'com.netflix.nebula.clojure' }", '''
            plugins { id 'java'; id 'com.netflix.nebula.clojure' apply false }
            tasks.named('jar').get()
            tasks.register('dormant', Jar)
            tasks.create('bundle', Jar) {
                archiveBaseName.set('additional')
                from('bundle.txt')
            }
            apply plugin: 'com.netflix.nebula.clojure'
            tasks.register('later', Jar)
            assert !tasks.names.contains('uberdormant')
            assert !tasks.names.contains('uberlater')
        '''.stripIndent())
        buildFile << "\ntasks.named('uberbundle') { enabled = true }\n"
        write('bundle.txt', 'additional Jar task contents')
        def requested = ['uberjar', 'uberbundle']
        def additional = new File(projectDir, 'build/libs/additional-standalone-1.0.jar')

        when:
        def first = execute(version, isolated, requested)
        def second = execute(version, isolated, requested)

        then:
        stored(first)
        reused(second)
        first.task(':uberjar').outcome == TaskOutcome.SKIPPED
        second.task(':uberjar').outcome == TaskOutcome.SKIPPED
        !uberArchive(':').exists()
        first.task(':uberbundle').outcome == TaskOutcome.SUCCESS
        second.task(':uberbundle').outcome == TaskOutcome.UP_TO_DATE
        new JarFile(additional).withCloseable {
            it.getInputStream(it.getEntry('bundle.txt')).text == 'additional Jar task contents' &&
                it.getEntry('clojure/main.class') != null
        }

        where:
        [version, isolated] << [VERSIONS, [false, true]].combinations()
    }

    @Unroll
    def 'root-only application stays local on Gradle #version, isolation=#isolated'() {
        given:
        fixture('single')
        settingsFile << "include 'child'\n"
        write('child/build.gradle', '''
            assert !plugins.hasPlugin('com.netflix.nebula.clojure')
            assert extensions.findByName('clojure') == null
            assert !tasks.names.contains('compileClojure')
        '''.stripIndent())
        def requested = [':build', ':child:tasks', '--all']

        when:
        def first = execute(version, isolated, requested)
        def second = execute(version, isolated, requested)

        then:
        stored(first)
        reused(second)
        first.task(':compileClojure').outcome == TaskOutcome.SUCCESS
        first.task(':clojureTest').outcome == TaskOutcome.SUCCESS
        first.task(':child:tasks').outcome == TaskOutcome.SUCCESS
        second.task(':compileClojure').outcome == TaskOutcome.UP_TO_DATE
        !first.tasks.any { it.path.startsWith(':child:compile') }

        where:
        [version, isolated] << [VERSIONS, [false, true]].combinations()
    }

    @Unroll
    def 'legacy ID with deps and Maven publishing on Gradle #version, isolation=#isolated'() {
        given:
        fixture('single')
        buildFile.text = buildFile.text.replace("id 'com.netflix.nebula.clojure'", "id 'nebula.clojure'; id 'maven-publish'")
        buildFile << '''
            description = 'Assessment documentation'
            publishing {
                publications { maven(MavenPublication) { from components.java } }
                repositories {
                    maven { name = 'assessment'; url = layout.buildDirectory.dir('repository') }
                }
            }
        '''.stripIndent()
        def requested = ['publishMavenPublicationToAssessmentRepository', 'deps']
        def published = new File(projectDir, 'build/repository/assessment/assessment/1.0/assessment-1.0.jar')

        when:
        def first = execute(version, isolated, requested)
        def second = execute(version, isolated, requested)

        then:
        stored(first)
        reused(second)
        first.task(':compileClojure').outcome == TaskOutcome.SUCCESS
        first.task(':deps').outcome == TaskOutcome.SUCCESS
        published.exists()
        new JarFile(published).withCloseable { it.getEntry('assessment/app__init.class') != null }
        new File(projectDir, 'lib/clojure-1.10.3.jar').exists()

        where:
        [version, isolated] << [VERSIONS, [false, true]].combinations()
    }

    @Unroll
    def 'build and documentation track actual inputs in #topology on Gradle #version, isolation=#isolated'() {
        given:
        def participants = fixture(topology)
        participants.each { path ->
            new File(projectDir, relative(path, 'build.gradle')) << '''
                description = 'Original documentation'
                tasks.named('clojuredoc') {
                    codox.put('src-dir-uri', 'https://example.test/original/')
                    codox.put('src-linenum-anchor-prefix', 'L')
                }
            '''.stripIndent()
            def source = new File(projectDir, relative(path, 'src/main/clojure/' + mainResource(path, '.clj')))
            source.text = source.text.replace('(defn message []', '(defn message "Original function documentation" []')
        }
        def requested = participants.collectMany { [taskPath(it, 'build'), taskPath(it, 'clojuredoc')] }
        def leaf = participants.last()
        def index = new File(projectDir, relative(leaf, 'build/docs/clojuredoc/index.html'))
        def page = new File(projectDir, relative(leaf, 'build/docs/clojuredoc/assessment.app.html'))

        when:
        def first = execute(version, isolated, requested)
        def second = execute(version, isolated, requested)

        then:
        stored(first)
        reused(second)
        participants.every { path ->
            first.task(taskPath(path, 'clojuredoc')).outcome == TaskOutcome.SUCCESS &&
                second.task(taskPath(path, 'clojuredoc')).outcome == TaskOutcome.UP_TO_DATE
        }
        index.text.contains('Original documentation')
        index.text.contains('1.0 API documentation')
        page.text.contains('Original function documentation')
        page.text.contains('https://example.test/original/src/main/clojure/assessment/app.clj#L')
        new File(index.parentFile, 'css/default.css').length() > 0
        new File(index.parentFile, 'js/page_effects.js').length() > 0
        new File(index.parentFile, 'js/jquery.min.js').length() > 0

        when: 'unrelated files and producer outputs change'
        write(relative(leaf, 'unrelated.txt'), 'unrelated edit')
        write(relative(leaf, 'src/main/clojure/README.md'), 'not documentation source')
        write(relative(leaf, 'build/unrelated/output.txt'), 'unrelated producer output')
        def unrelated = execute(version, isolated, requested)

        then:
        reused(unrelated)
        participants.every { unrelated.task(taskPath(it, 'clojuredoc')).outcome == TaskOutcome.UP_TO_DATE }

        when: 'generated documentation is deleted while configuration stays cached'
        assert index.parentFile.deleteDir()
        def regenerated = execute(version, isolated, requested)

        then:
        reused(regenerated)
        regenerated.task(taskPath(leaf, 'clojuredoc')).outcome == TaskOutcome.SUCCESS
        page.text.contains('Original function documentation')

        when: 'source content changes'
        def source = new File(projectDir, relative(leaf, 'src/main/clojure/assessment/app.clj'))
        source.text = source.text.replace('Original function documentation', 'Updated function documentation')
        def changedSource = execute(version, isolated, requested)

        then:
        reused(changedSource)
        changedSource.task(taskPath(leaf, 'clojuredoc')).outcome == TaskOutcome.SUCCESS
        page.text.contains('Updated function documentation')

        when: 'documentation options change independently of project metadata'
        new File(projectDir, relative(leaf, 'build.gradle')) << '''
            tasks.named('clojuredoc') {
                codox.put('src-dir-uri', 'https://example.test/updated/')
            }
        '''.stripIndent()
        def changedOptions = execute(version, isolated, requested)

        then:
        stored(changedOptions)
        changedOptions.task(taskPath(leaf, 'clojuredoc')).outcome == TaskOutcome.SUCCESS
        page.text.contains('https://example.test/updated/src/main/clojure/assessment/app.clj#L')

        when: 'metadata configured after plugin application changes'
        new File(projectDir, relative(leaf, 'build.gradle')) << '''
            description = 'Updated project documentation'
            version = '2.0'
            tasks.named('clojuredoc') {
                projectName.set('renamed')
            }
        '''.stripIndent()
        def changedMetadata = execute(version, isolated, requested)
        def finalReuse = execute(version, isolated, requested)

        then:
        stored(changedMetadata)
        changedMetadata.task(taskPath(leaf, 'clojuredoc')).outcome == TaskOutcome.SUCCESS
        index.text.contains('Renamed 2.0 API documentation')
        index.text.contains('Updated project documentation')
        page.text.contains('https://example.test/updated/src/main/clojure/assessment/app.clj#L')
        reused(finalReuse)
        finalReuse.task(taskPath(leaf, 'clojuredoc')).outcome == TaskOutcome.UP_TO_DATE

        where:
        [version, isolated, topology] << [VERSIONS, [false, true], ['single', 'multi', 'nested']].combinations()
    }

    @Unroll
    def 'documentation tracks source-relative paths on Gradle #version, isolation=#isolated'() {
        given:
        fixture('single')
        buildFile << '''
            tasks.named('clojuredoc') {
                codox.put('src-dir-uri', 'https://example.test/')
                // Keep the public DirectoryProperty API for source-link path calculations.
                projectDirectory.set(layout.projectDirectory)
            }
        '''.stripIndent()
        def page = new File(projectDir, 'build/docs/clojuredoc/assessment.app.html')

        when:
        def first = execute(version, isolated, ['clojuredoc'])
        def second = execute(version, isolated, ['clojuredoc'])

        then:
        stored(first)
        reused(second)
        page.text.contains('https://example.test/src/main/clojure/assessment/app.clj')

        when: 'the same source contents move to a different relative source directory'
        def target = new File(projectDir, 'src/alternate/assessment/app.clj')
        target.parentFile.mkdirs()
        target.bytes = new File(projectDir, 'src/main/clojure/assessment/app.clj').bytes
        buildFile << '''
            tasks.named('clojuredoc') {
                setSrcDirs([file('src/alternate')])
            }
        '''.stripIndent()
        def moved = execute(version, isolated, ['clojuredoc'])
        def movedReuse = execute(version, isolated, ['clojuredoc'])

        then:
        stored(moved)
        moved.task(':clojuredoc').outcome == TaskOutcome.SUCCESS
        reused(movedReuse)
        movedReuse.task(':clojuredoc').outcome == TaskOutcome.UP_TO_DATE
        // The relative directory participates in input tracking even when the file tree is identical.
        page.text.contains('https://example.test/src/alternate/assessment/app.clj')

        where:
        [version, isolated] << [VERSIONS, [false, true]].combinations()
    }

    @Unroll
    def 'documentation reflects dependency contents on Gradle #version, isolation=#isolated'() {
        given:
        fixture('single')
        buildFile << "\ndependencies { implementation files('support.jar') }\n"
        def source = new File(projectDir, 'src/main/clojure/assessment/app.clj')
        source << '''
            (require 'documentation.support)
            (alter-meta! #'message assoc :doc documentation.support/text)
        '''.stripIndent()
        writeJar('support.jar', ['documentation/support.clj': '(ns documentation.support) (def text "Dependency documentation v1")'])
        def page = new File(projectDir, 'build/docs/clojuredoc/assessment.app.html')

        when:
        def first = execute(version, isolated, ['build', 'clojuredoc'])
        def second = execute(version, isolated, ['build', 'clojuredoc'])

        then:
        stored(first)
        reused(second)
        second.task(':clojuredoc').outcome == TaskOutcome.UP_TO_DATE
        page.text.contains('Dependency documentation v1')

        when: 'a dependency changes in place with configuration unchanged'
        writeJar('support.jar', ['documentation/support.clj': '(ns documentation.support) (def text "Dependency documentation v2")'])
        def changed = execute(version, isolated, ['build', 'clojuredoc'])
        def changedReuse = execute(version, isolated, ['build', 'clojuredoc'])

        then:
        reused(changed)
        changed.task(':clojuredoc').outcome == TaskOutcome.SUCCESS
        page.text.contains('Dependency documentation v2')
        !page.text.contains('Dependency documentation v1')
        reused(changedReuse)
        changedReuse.task(':clojuredoc').outcome == TaskOutcome.UP_TO_DATE

        where:
        [version, isolated] << [VERSIONS, [false, true]].combinations()
    }

    private List<String> uberFixture(String topology) {
        def participants = fixture(topology)
        write('gradle.properties', 'assessment.aot=false\n')
        participants.each { path ->
            def script = new File(projectDir, relative(path, 'build.gradle'))
            script.text = script.text.replace("plugins { id 'com.netflix.nebula.clojure' }", '''
                plugins { id 'java'; id 'com.netflix.nebula.clojure' apply false }
                // Retain the existing snapshot of already-realized Jar tasks.
                tasks.named('jar').get()
                tasks.register('dormant', Jar)
                apply plugin: 'com.netflix.nebula.clojure'
                tasks.register('later', Jar)
                assert !tasks.names.contains('uberdormant')
                assert !tasks.names.contains('uberlater')
            '''.stripIndent())
            script << '''
                dependencies { runtimeOnly files('runtime-only.jar') }
                tasks.named('uberjar') {
                    enabled = true
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                    manifest { attributes('Consumer-Manifest': 'kept') }
                    from('uber-extra.txt') { into 'consumer' }
                    exclude 'drop-me.txt'
                }
                tasks.named('uberjar').get()
                // Original Jar customization after the uber task is realized must still work.
                tasks.named('jar') { from('jar-extra.txt') }
            '''.stripIndent()
            write(relative(path, 'jar-extra.txt'), 'original Jar customization')
            write(relative(path, 'uber-extra.txt'), 'uber Jar customization')
            write(relative(path, 'src/main/resources/application.txt'), 'application resources')
            writeRuntimeJar(path, 'runtime-v1')
        }
        participants
    }

    private void writeRuntimeJar(String path, String value) {
        def marker = path == ':' ? 'root' : path.tokenize(':').last()
        writeJar(relative(path, 'runtime-only.jar'), [
            ("runtime/${marker}.txt".toString()): value,
            ("META-INF/services/${marker}".toString()): 'service resource',
            'META-INF/MANIFEST.MF': 'Manifest-Version: 1.0\nDependency-Only: excluded\n\n',
            'META-INF/DEPENDENCY.SF': 'signature',
            'META-INF/DEPENDENCY.DSA': 'signature',
            'META-INF/DEPENDENCY.RSA': 'signature',
            'drop-me.txt': 'excluded by consumer'
        ])
    }

    private File uberArchive(String path) {
        def name = path == ':' ? 'assessment' : path.tokenize(':').last()
        new File(projectDir, relative(path, "build/libs/${name}-standalone-1.0.jar"))
    }

    private boolean assertUberArchive(String path, String sourceValue, String runtimeValue, File archive = uberArchive(path)) {
        def marker = path == ':' ? 'root' : path.tokenize(':').last()
        new JarFile(archive).withCloseable { jar ->
            def entries = jar.entries().collect { it.name }
            assert entries.contains(mainResource(path, '.clj'))
            assert entries.contains('clojure/main.class')
            assert entries.contains("META-INF/services/$marker".toString())
            assert !entries.any { it ==~ /META-INF\/.*\.(SF|DSA|RSA)/ }
            assert !entries.contains('drop-me.txt')
            assert entries.count { it == 'META-INF/MANIFEST.MF' } == 1
            assert jar.manifest.mainAttributes.getValue('Consumer-Manifest') == 'kept'
            assert jar.manifest.mainAttributes.getValue('Dependency-Only') == null
            assert jar.getInputStream(jar.getEntry(mainResource(path, '.clj'))).text.contains(sourceValue)
            assert jar.getInputStream(jar.getEntry("runtime/${marker}.txt")).text == runtimeValue
            assert jar.getInputStream(jar.getEntry('jar-extra.txt')).text == 'original Jar customization'
            assert jar.getInputStream(jar.getEntry('consumer/uber-extra.txt')).text == 'uber Jar customization'
            assert jar.getInputStream(jar.getEntry('application.txt')).text == 'application resources'
        }
        true
    }

    private List<String> fixture(String topology) {
        def participants = topology == 'single' ? [':'] :
            (topology == 'multi' ? [':library', ':app'] : [':library', ':group:app'])
        settingsFile.text = "rootProject.name = 'assessment'\n"
        if (topology != 'single') {
            settingsFile << "include ${participants.collect { "'${it.substring(1)}'" }.join(', ')}\n"
            buildFile.text = ''
            if (topology == 'nested') write('group/build.gradle', '')
        }
        participants.eachWithIndex { String path, int index ->
            def dependency = index == 0 ? '' : "implementation project(':library')"
            def namespace = path == ':library' ? 'assessment.library' : 'assessment.app'
            def require = index == 0 ? '' : '(:require [assessment.library :as library])'
            def message = index == 0 ? '"app-v1"' : '(str "app-v1 dependency=" (library/message))'
            write(relative(path, 'build.gradle'), """
                plugins { id 'com.netflix.nebula.clojure' }
                group = 'assessment'
                version = '1.0'
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.clojure:clojure:1.10.3'
                    $dependency
                }
                clojure.aotCompile.set(providers.gradleProperty('assessment.aot').map { it.toBoolean() }.orElse(true))
                tasks.named('clojureTest') {
                    jvmOptions { systemProperty 'assessment.jvm', 'configured' }
                }
                tasks.named('clojureRun') {
                    fn.set('${namespace}/greet')
                    jvmOptions { systemProperty 'assessment.jvm', 'configured' }
                }
            """.stripIndent())
            write(relative(path, 'src/main/clojure/' + mainResource(path, '.clj')), """
                (ns $namespace $require)
                (defn message [] $message)
                (defn greet []
                  (assert (= "configured" (System/getProperty "assessment.jvm")))
                  (println (message)))
            """.stripIndent())
            write(relative(path, 'src/test/clojure/assessment/app_test.clj'), """
                (ns assessment.app-test
                  (:require [clojure.test :refer :all] [$namespace :as app]))
                (deftest passing
                  (is (string? (app/message)))
                  (is (= "configured" (System/getProperty "assessment.jvm"))))
                (deftest other (println "unselected-test") (is true))
            """.stripIndent())
        }
        participants
    }

    private BuildResult execute(String version, boolean isolated, List<String> tasks) {
        // Isolated Projects used the unsafe property before Gradle 9.7.
        def isolationProperty = GradleVersion.version(version) >= GradleVersion.version('9.7') ?
            'org.gradle.isolated-projects' : 'org.gradle.unsafe.isolated-projects'
        def arguments = tasks + ['--configuration-cache', '--configuration-cache-problems=fail',
                                 "-D${isolationProperty}=${isolated}"]
        def result = runner(version, arguments).build()
        if (isolated && !result.output.contains('Configuration cache entry reused')) {
            assert result.output.toLowerCase().contains('isolated projects')
        }
        result
    }

    private GradleRunner runner(String version, List<String> arguments) {
        GradleRunner.create().withProjectDir(projectDir).withPluginClasspath()
            .withGradleVersion(version).withArguments(
                (arguments + ['--stacktrace', '--console=plain', '--max-workers=2']).collect { it.toString() })
    }

    private static boolean stored(BuildResult result) {
        assert result.output.contains('Configuration cache entry stored')
        assert !result.output.contains('Configuration cache problems found')
        true
    }

    private static boolean reused(BuildResult result) {
        assert result.output.contains('Configuration cache entry reused')
        assert !result.output.contains('Configuration cache problems found')
        true
    }

    private Set<String> jarEntries(String path) {
        def name = path == ':' ? 'assessment' : path.tokenize(':').last()
        new JarFile(new File(projectDir, relative(path, "build/libs/${name}-1.0.jar"))).withCloseable {
            it.entries().collect { it.name } as Set
        }
    }

    private void write(String path, String content) {
        def target = new File(projectDir, path)
        target.parentFile.mkdirs()
        target.text = content
    }

    private void writeJar(String path, Map<String, String> contents) {
        def target = new File(projectDir, path)
        target.parentFile.mkdirs()
        new JarOutputStream(new FileOutputStream(target)).withCloseable { jar ->
            contents.each { name, content ->
                jar.putNextEntry(new JarEntry(name))
                jar.write(content.getBytes('UTF-8'))
                jar.closeEntry()
            }
        }
    }

    private static String relative(String projectPath, String path) {
        (projectPath == ':' ? '' : projectPath.substring(1).replace(':', '/') + '/') + path
    }

    private static String mainResource(String path, String suffix) {
        (path == ':library' ? 'assessment/library' : 'assessment/app') + suffix
    }

    private static String taskPath(String projectPath, String task) {
        (projectPath == ':' ? '' : projectPath) + ':' + task
    }
}
