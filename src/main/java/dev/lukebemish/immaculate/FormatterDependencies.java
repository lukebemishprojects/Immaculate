package dev.lukebemish.immaculate;

import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyCollector;

@SuppressWarnings("UnstableApiUsage")
public abstract class FormatterDependencies implements Dependencies {
    public abstract DependencyCollector getRuntime();
}
