package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.ForkFormatterSpec;
import dev.lukebemish.immaculate.ImmaculatePlugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import javax.inject.Inject;

public abstract class EclipseJdtFormatStep extends WrapperFormattingStep {
    @Inject
    public EclipseJdtFormatStep(String name, String workflowName, Project project, ObjectFactory objectFactory) {
        super(name, workflowName, project, objectFactory);
        this.getDependencies().getRuntime().add("dev.lukebemish.immaculate.wrapper:eclipse-jdt", dep -> {
            if (ImmaculatePlugin.PLUGIN_VERSION != null) {
                dep.version(constraint ->
                    constraint.require(ImmaculatePlugin.PLUGIN_VERSION)
                );
            }
        });
        this.formatterDependency = objectFactory.property(Dependency.class);
        formatterDependency.convention(getDependencies().module(MAVEN_PATH + ":" + DefaultVersions.ECLIPSE_JDT));
        getDependencies().getRuntime().add(formatterDependency);
    }

    private static final String MAVEN_PATH = "org.eclipse.jdt:org.eclipse.jdt.core";

    private transient final Property<Dependency> formatterDependency;

    @Internal
    protected Property<Dependency> getEclipseJdtFormatter() {
        return formatterDependency;
    }

    public void version(String version) {
        formatterDependency.set(getDependencies().module(MAVEN_PATH + ":" + version));
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
