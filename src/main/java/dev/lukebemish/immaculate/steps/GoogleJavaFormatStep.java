package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.ForkFormatterSpec;
import dev.lukebemish.immaculate.ImmaculatePlugin;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;

import javax.inject.Inject;
import java.util.List;

public abstract class GoogleJavaFormatStep extends WrapperFormattingStep {
    @Inject
    public GoogleJavaFormatStep() {
        this.getDependencies().getRuntime().add("dev.lukebemish.immaculate.wrapper:google-java-format", dep -> {
            if (ImmaculatePlugin.PLUGIN_VERSION != null) {
                dep.version(constraint ->
                    constraint.require(ImmaculatePlugin.PLUGIN_VERSION)
                );
            }
        });
        this.formatterDependency = getObjectFactory().property(Dependency.class);
        getVersion().convention(DefaultVersions.GOOGLE_JAVA_FORMAT);
        formatterDependency.convention(getVersion().map(v -> getDependencies().module(MAVEN_PATH + ":" + v)));
        getDependencies().getRuntime().add(formatterDependency);
    }

    private static final String MAVEN_PATH = "com.google.googlejavaformat:google-java-format";

    private transient final Property<Dependency> formatterDependency;

    @Internal
    protected Property<Dependency> getGoogleJavaFormatter() {
        return formatterDependency;
    }

    @Internal
    protected abstract Property<String> getVersion();

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

    public void fixImports() {
        getArgs().addAll("--fix-imports-only");
    }

    public void removeUnusedImports() {
        getArgs().addAll("--fix-imports-only", "--skip-sorting-imports");
    }

    public void sortImports() {
        getArgs().addAll("--fix-imports-only", "--skip-removing-unused-imports");
    }

    public void aosp() {
        getArgs().addAll("--aosp");
    }
}
