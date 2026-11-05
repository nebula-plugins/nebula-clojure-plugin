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
takes precedence over Gradle properties, including `-P`. Set this local extra
before `clojureTest` is realized; it is read during lazy task configuration, so
setting it after plugin application is supported. Changing the extra after task
realization does not change the captured selection. Explicit task configuration
such as `tasks.named('clojureTest') { tests.set(['example.app-test/second-test']) }`
overrides the selector. `tests.set([])` runs all tests. Selection changes are
tracked task inputs and cause the newly selected tests to run. These selection
rules apply to the default `junit = false` runner; the existing `junit = true`
runner continues to run whole namespaces.

**Adoption requirement:** parent-only extra properties are no longer inherited as
selectors, in either ordinary builds or Isolated Projects builds. Move shared
values from a root or parent `ext` to `gradle.properties` or `-P`, or configure
`tests`/the local extra in each participating project, preferably through a
locally applied convention plugin. Apply the Clojure plugin locally in each
participating project as well; root-only application remains root-only.

The selector path is tested with strict configuration-cache validation in single,
sibling, and nested projects on Gradle 8.0 and the current wrapper (9.2.1), both
with and without Isolated Projects. This is a bounded fix for
selector ownership and input tracking. Enabled uber-JAR configuration-cache
issues and overly broad documentation inputs remain unresolved; whole-plugin
Isolated Projects compatibility is not claimed. These tests do not define a new
minimum supported Gradle/JDK version.
