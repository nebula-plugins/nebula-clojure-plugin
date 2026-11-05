nebula-clojure-plugin
==============

![Support Status](https://img.shields.io/badge/nebula-active-green.svg)
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/com.netflix.nebula/nebula-clojure-plugin/maven-metadata.xml.svg?label=gradlePluginPortal)](https://plugins.gradle.org/plugin/nebula.clojure)
[![Maven Central](https://img.shields.io/maven-central/v/com.netflix.nebula/nebula-clojure-plugin)](https://maven-badges.herokuapp.com/maven-central/com.netflix.nebula/nebula-clojure-plugin)
![Build](https://github.com/nebula-plugins/nebula-clojure-plugin/actions/workflows/nebula.yml/badge.svg)
[![MIT](https://img.shields.io/github/license/nebula-plugins/nebula-clojure-plugin.svg)](https://opensource.org/licenses/MIT)


Plugin providing Clojuresque with compatibility fixes for Gradle 8.x

# Quick Start

Refer to the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/nebula.clojure) for instructions on how to apply the plugin.

# Selecting Clojure tests

Use `./gradlew clojureTest -Pclojuresque.test.vars=example.app-test/first-test`
to select test vars. Separate multiple vars with commas. The selector can also be
set in `gradle.properties` as `clojuresque.test.vars=example.app-test/first-test`;
`-P` overrides that value. An absent or empty selector runs all tests.

A project's own `ext.set('clojuresque.test.vars', 'example.app-test/first-test')`
takes precedence over `-P` and `gradle.properties`. It can be set after plugin
application, but must be set before `clojureTest` is realized. Later changes to
the extra do not affect the captured selection.

Explicit task configuration overrides the selector:

```groovy
tasks.named('clojureTest') {
    tests.set(['example.app-test/second-test'])
}
```

`tests.set([])` runs all tests. Selection changes are tracked task inputs and
execute the newly selected tests. These rules apply to the default `junit = false`
runner; `junit = true` continues to run whole namespaces.

**Adoption requirement:** parent-only extra properties are no longer inherited as
selectors in either build mode. Move shared values to `gradle.properties` or `-P`,
or configure `tests`/the local extra in each participating project. Each project
must also apply the Clojure plugin, directly or through a local convention plugin.

The selector path is tested with strict configuration-cache validation in single,
sibling, and nested projects on Gradle 8.0 and the current wrapper (9.2.1), both
with and without Isolated Projects. This coverage establishes
selector behavior only, without claiming whole-plugin isolation compatibility or
defining a new minimum supported Gradle/JDK version.

# Documentation inputs

`build clojuredoc` can run together with strict configuration-cache validation,
including Isolated Projects, in single, sibling, and nested projects where each
participating project applies and configures the plugin locally. Coverage uses
Gradle 8.0 on a compatible JVM and the current wrapper (9.2.1).

Documentation tracks Clojure source files, relative source-directory paths,
classpath dependencies, Codox options, and project name, description, and version.
`ClojureDoc.projectDirectory` remains a `DirectoryProperty` used as the base for
source-link paths; unrelated project files and build outputs are no longer inputs.
Generated HTML, source links, static assets, configuration-cache reuse, and
regeneration after output deletion are verified.

These checks do not define a new minimum supported Gradle/JDK version or establish
whole-plugin isolation compatibility. Cross-project consumer configuration must
still be replaced with local configuration for Isolated Projects. Enabled
uber-JAR tasks remain a separate configuration-cache limitation.
