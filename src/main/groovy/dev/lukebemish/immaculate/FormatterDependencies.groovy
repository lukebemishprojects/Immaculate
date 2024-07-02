package dev.lukebemish.immaculate

import groovy.transform.CompileStatic;
import org.gradle.api.artifacts.dsl.Dependencies
import org.gradle.api.artifacts.dsl.DependencyCollector

@SuppressWarnings('UnstableApiUsage')
@CompileStatic
abstract class FormatterDependencies implements Dependencies {
    abstract DependencyCollector getRuntime();
}
