nebula-clojure-plugin
==============

Small wrapper around clojuresque, to avoid the including of clojars logic. We found that the clojars plugin. We're still
just applying the other core clojuresque plugins.

## Applying the Plugin

To include, add the following to your build.gradle

    buildscript {
      repositories { jcenter() }
      repositories { maven { url 'http://clojars.org/repo' } }

      dependencies {
        classpath 'com.netflix.nebula:nebula-clojure-plugin:2.2.+'
      }
    }

    apply plugin: 'nebula-clojure'

-or-

    buildscript {
    	repositories { maven { url 'http://clojars.org/repo' } }
    }

    plugins {
    	id 'nebula.nebula-clojure' version '2.2.0'
    }
