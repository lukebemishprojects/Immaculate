package dev.lukebemish.immaculate;

import org.gradle.api.artifacts.dsl.Dependencies
import org.gradle.api.artifacts.dsl.DependencyCollector

@SuppressWarnings('UnstableApiUsage')
abstract class Formatter implements Dependencies {
    abstract DependencyCollector getRuntime();
}
