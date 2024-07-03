package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.ForkFormatterSpec;
import dev.lukebemish.immaculate.ImmaculatePlugin;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.jvm.toolchain.JavaToolchainService;

import javax.inject.Inject;
import java.util.List;

public abstract class GoogleJavaFormatStep extends WrapperFormattingStep {
    @Inject
    public GoogleJavaFormatStep(String name, String workflowName, Project project, ObjectFactory objectFactory, JavaToolchainService javaToolchainService) {
        super(name, workflowName, project, objectFactory, javaToolchainService);
        this.getFormatter().getRuntime().add("dev.lukebemish.immaculate.wrapper:google-java-format", dep -> {
            if (ImmaculatePlugin.PLUGIN_VERSION != null) {
                dep.version(constraint ->
                    constraint.require(ImmaculatePlugin.PLUGIN_VERSION)
                );
            }
        });
    }

    private static final String MAVEN_PATH = "com.google.googlejavaformat:google-java-format";
    private static final String DEFAULT_VERSION = "1.22.0";

    public void defaultVersion() {
        version(DEFAULT_VERSION);
    }

    @SuppressWarnings("UnstableApiUsage")
    public void version(String version) {
        getFormatter().getRuntime().add(MAVEN_PATH + ":" + version);
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
