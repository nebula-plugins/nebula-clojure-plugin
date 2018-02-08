package nebula.plugin.clojure

import nebula.plugin.clojuresque.tasks.ClojureExecAction
import nebula.test.ProjectSpec
import org.gradle.api.internal.project.ProjectInternal

class ClojureExecActionSpec extends ProjectSpec {
    ClojureExecAction action

    def setup() {
        action = new ClojureExecAction( ((ProjectInternal)project).getFileResolver(), project.gradle.gradleVersion)
    }

    def 'default args'() {
        given:
        List<String> args = action.getArgs()

        expect:
        args.size() == 1
        args[0] == '-'
    }

    def 'update args'() {
        given:
        action.setMain('testfoo')
        List<String> args = action.getArgs()

        expect:
        args.size() == 2
        args[0] == '-m'
        args[1] == 'testfoo'
    }

    def 'update args back to original'() {
        given:
        action.setMain('testfoo')
        action.setMain('-')
        List<String> args = action.getArgs()

        expect:
        args.size() == 1
        args[0] == '-'
    }

    def 'preserve initial args'() {
        given:
        action.args('arg1', 'arg2')
        action.setMain('testfoo')
        List<String> args = action.getArgs()

        expect:
        args.size() == 4
        args == ['-m', 'testfoo', 'arg1', 'arg2']
    }
}
