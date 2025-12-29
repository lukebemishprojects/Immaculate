package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.ForkFormatterSpec;
import dev.lukebemish.immaculate.ImmaculatePlugin;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;

import javax.inject.Inject;
import java.util.List;

public abstract class PalantirJavaFormatStep extends WrapperFormattingStep {

    private static final String MAVEN_PATH = "com.palantir.javaformat:palantir-java-format";
    private static final List<String> GOOGLE_JAVA_FORMAT_ADD_EXPORTS = List.of(
        "jdk.compiler/com.sun.tools.javac.api", "jdk.compiler/com.sun.tools.javac.code",
        "jdk.compiler/com.sun.tools.javac.file", "jdk.compiler/com.sun.tools.javac.parser",
        "jdk.compiler/com.sun.tools.javac.tree", "jdk.compiler/com.sun.tools.javac.util"
    );
    private final transient Property<Dependency> formatterDependency;

    @Inject
    public PalantirJavaFormatStep() {
        this.getDependencies().getRuntime().add("dev.lukebemish.immaculate.wrapper:palantir-java-format", dep -> {
            if (ImmaculatePlugin.PLUGIN_VERSION != null) {
                dep.version(constraint ->
                    constraint.require(ImmaculatePlugin.PLUGIN_VERSION)
                );
            }
        });
        this.formatterDependency = getObjectFactory().property(Dependency.class);
        this.formatterDependency.convention(this.getDependencies().module(MAVEN_PATH + ":" + DefaultVersions.PALANTIR_JAVA_FORMAT));
        this.getDependencies().getRuntime().add(this.formatterDependency);
    }

    @Internal
    protected Property<Dependency> getGoogleJavaFormatter() {
        return this.formatterDependency;
    }

    public void version(String version) {
        this.formatterDependency.set(this.getDependencies().module(MAVEN_PATH + ":" + version));
    }

    @Override
    protected void configureSpec(ForkFormatterSpec spec) {
        GOOGLE_JAVA_FORMAT_ADD_EXPORTS.forEach(e -> spec.getJvmArgs().addAll("--add-exports", e + "=ALL-UNNAMED"));
        spec.getWrapperClass().set("dev.lukebemish.immaculate.wrapper.palantirjavaformat.PalantirJavaFormatWrapper");
    }
}
