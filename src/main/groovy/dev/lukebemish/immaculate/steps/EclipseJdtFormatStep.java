package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.ForkFormatterSpec;
import dev.lukebemish.immaculate.ImmaculatePlugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.jvm.toolchain.JavaToolchainService;

import javax.inject.Inject;

public abstract class EclipseJdtFormatStep extends WrapperFormattingStep {
    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public EclipseJdtFormatStep(String name, String workflowName, Project project, ObjectFactory objectFactory, JavaToolchainService javaToolchainService) {
        super(name, workflowName, project, objectFactory, javaToolchainService);
        this.getFormatter().getRuntime().add("dev.lukebemish.immaculate.wrapper:eclipse-jdt", dep -> {
            if (ImmaculatePlugin.PLUGIN_VERSION != null) {
                dep.version(constraint ->
                    constraint.require(ImmaculatePlugin.PLUGIN_VERSION)
                );
            }
        });
    }

    private static final String MAVEN_PATH = "org.eclipse.jdt:org.eclipse.jdt.core";
    private static final String DEFAULT_VERSION = "3.38.0";

    public void defaultVersion() {
        version(DEFAULT_VERSION);
    }

    @SuppressWarnings("UnstableApiUsage")
    public void version(String version) {
        getFormatter().getRuntime().add(MAVEN_PATH + ":" + version);
    }

    @Override
    protected void configureSpec(ForkFormatterSpec spec) {
        spec.getWrapperClass().set("dev.lukebemish.immaculate.wrapper.eclipsejdt.EclipseJdtWrapper");
        if (getConfig().isPresent()) {
            spec.getProgramArgs().add(getConfig().get().getAsFile().getAbsolutePath());
        }
    }

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getConfig();
}
