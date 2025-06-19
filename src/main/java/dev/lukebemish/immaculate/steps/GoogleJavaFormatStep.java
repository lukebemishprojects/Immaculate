package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.ForkFormatterSpec;
import dev.lukebemish.immaculate.ImmaculatePlugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;

import javax.inject.Inject;
import java.util.List;

public abstract class GoogleJavaFormatStep extends WrapperFormattingStep {
    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public GoogleJavaFormatStep(String name, String workflowName, Project project, ObjectFactory objectFactory) {
        super(name, workflowName, project, objectFactory);
        this.getDependencies().getRuntime().add("dev.lukebemish.immaculate.wrapper:google-java-format", dep -> {
            if (ImmaculatePlugin.PLUGIN_VERSION != null) {
                dep.version(constraint ->
                    constraint.require(ImmaculatePlugin.PLUGIN_VERSION)
                );
            }
        });
        this.formatterDependency = objectFactory.property(Dependency.class);
        formatterDependency.convention(getDependencies().module(MAVEN_PATH + ":" + DEFAULT_VERSION));
        getDependencies().getRuntime().add(formatterDependency);
    }

    private static final String MAVEN_PATH = "com.google.googlejavaformat:google-java-format";
    private static final String DEFAULT_VERSION = "1.22.0";

    private transient final Property<Dependency> formatterDependency;

    @Internal
    protected Property<Dependency> getGoogleJavaFormatter() {
        return formatterDependency;
    }

    @SuppressWarnings("UnstableApiUsage")
    public void version(String version) {
        formatterDependency.set(getDependencies().module(MAVEN_PATH + ":" + version));
    }

    private static final List<String> GOOGLE_JAVA_FORMAT_ADD_EXPORTS = List.of(
            "jdk.compiler/com.sun.tools.javac.api", "jdk.compiler/com.sun.tools.javac.code",
            "jdk.compiler/com.sun.tools.javac.file", "jdk.compiler/com.sun.tools.javac.parser",
            "jdk.compiler/com.sun.tools.javac.tree", "jdk.compiler/com.sun.tools.javac.util"
    );

    @Override
    protected void configureSpec(ForkFormatterSpec spec) {
        GOOGLE_JAVA_FORMAT_ADD_EXPORTS.forEach(e -> spec.getJvmArgs().addAll("--add-exports", e + "=ALL-UNNAMED"));
        spec.getWrapperClass().set("dev.lukebemish.immaculate.wrapper.googlejavaformat.GoogleJavaFormatWrapper");
    }
}
