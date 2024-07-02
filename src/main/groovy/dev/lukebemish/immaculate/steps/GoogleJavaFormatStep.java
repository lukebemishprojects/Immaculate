package dev.lukebemish.immaculate.steps;

import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class GoogleJavaFormatStep extends ExternalFormattingStep {
    private final ExecOperations execOperations;

    @Inject
    public GoogleJavaFormatStep(String name, String workflowName, Project project, ObjectFactory objectFactory, ExecOperations execOperations, JavaToolchainService javaToolchainService) {
        super(name, workflowName, project, objectFactory);
        getJavaLauncher().convention(javaToolchainService.launcherFor(spec -> {}));
        this.execOperations = execOperations;
    }

    private static final String MAVEN_PATH = "com.google.googlejavaformat:google-java-format";
    private static final String DEFAULT_VERSION = "1.22.0";

    @SuppressWarnings("UnstableApiUsage")
    public void defaultVersion() {
        getFormatter().getRuntime().add(MAVEN_PATH + ":" + DEFAULT_VERSION);
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
    public String fix(String fileName, String text) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        var result = execOperations.javaexec(spec -> {
            spec.setIgnoreExitValue(true);
            spec.executable(getJavaLauncher().get().getExecutablePath());
            spec.args('-');
            spec.classpath(getFormatterClasspath());
            spec.getMainClass().set("com.google.googlejavaformat.java.Main");

            // Fix up module stuff
            GOOGLE_JAVA_FORMAT_ADD_EXPORTS.forEach(e -> spec.jvmArgs("--add-exports", e+"=ALL-UNNAMED"));

            spec.args(getArgs().get());
            spec.setStandardInput(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
            spec.setStandardOutput(outputStream);
        });
        if (result.getExitValue() != 0) {
            System.out.println(outputStream.toString(StandardCharsets.UTF_8));
        }
        result.rethrowFailure().assertNormalExitValue();
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    @Input
    public abstract ListProperty<String> getArgs();

    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();
}
