package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.ForkFormatterSpec;
import dev.lukebemish.immaculate.ImmaculatePlugin;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.jvm.toolchain.JavaToolchainService;

public abstract class PalantirJavaFormatStep extends WrapperFormattingStep {

    private static final String MAVEN_PATH = "com.palantir.javaformat:palantir-java-format";
    private static final String DEFAULT_VERSION = "2.50.0";
    private static final List<String> GOOGLE_JAVA_FORMAT_ADD_EXPORTS = List.of(
        "jdk.compiler/com.sun.tools.javac.api", "jdk.compiler/com.sun.tools.javac.code",
        "jdk.compiler/com.sun.tools.javac.file", "jdk.compiler/com.sun.tools.javac.parser",
        "jdk.compiler/com.sun.tools.javac.tree", "jdk.compiler/com.sun.tools.javac.util"
    );
    private final transient Property<Dependency> formatterDependency;

    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public PalantirJavaFormatStep(final String name, final String workflowName, final Project project, final ObjectFactory objectFactory, final JavaToolchainService javaToolchainService) {
        super(name, workflowName, project, objectFactory, javaToolchainService);
        this.getDependencies().getRuntime().add("dev.lukebemish.immaculate.wrapper:palantir-java-format", dep -> {
            if (ImmaculatePlugin.PLUGIN_VERSION != null) {
                dep.version(constraint ->
                    constraint.require(ImmaculatePlugin.PLUGIN_VERSION)
                );
            }
        });
        this.formatterDependency = objectFactory.property(Dependency.class);
        this.formatterDependency.convention(this.getDependencies().module(MAVEN_PATH + ":" + DEFAULT_VERSION));
        this.getDependencies().getRuntime().add(this.formatterDependency);
    }

    @Internal
    protected Property<Dependency> getGoogleJavaFormatter() {
        return this.formatterDependency;
    }

    @SuppressWarnings("UnstableApiUsage")
    public void version(final String version) {
        this.formatterDependency.set(this.getDependencies().module(MAVEN_PATH + ":" + version));
    }

    @Override
    protected void configureSpec(final ForkFormatterSpec spec) {
        GOOGLE_JAVA_FORMAT_ADD_EXPORTS.forEach(e -> spec.getJvmArgs().addAll("--add-exports", e + "=ALL-UNNAMED"));
        spec.getWrapperClass().set("dev.lukebemish.immaculate.wrapper.palantirjavaformat.PalantirJavaFormatWrapper");
    }
}
