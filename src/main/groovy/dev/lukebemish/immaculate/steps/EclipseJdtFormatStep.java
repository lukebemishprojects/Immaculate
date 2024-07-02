package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.ImmaculatePlugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public abstract class EclipseJdtFormatStep extends ExternalFormattingStep {
    private final ExecOperations execOperations;

    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public EclipseJdtFormatStep(String name, String workflowName, Project project, ObjectFactory objectFactory, ExecOperations execOperations, JavaToolchainService javaToolchainService) {
        super(name, workflowName, project, objectFactory);
        getJavaLauncher().convention(javaToolchainService.launcherFor(spec -> {}));
        this.execOperations = execOperations;
        this.getFormatter().getRuntime().add("dev.lukebemish.immaculate:eclipse-jdt-wrapper", dep -> {
            if (ImmaculatePlugin.PLUGIN_VERSION != null) {
                dep.version(constraint ->
                    constraint.require(ImmaculatePlugin.PLUGIN_VERSION)
                );
            }
        });
    }

    @Override
    public String fix(String fileName, String text) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        var result = execOperations.javaexec(spec -> {
            spec.setIgnoreExitValue(true);
            spec.executable(getJavaLauncher().get().getExecutablePath());
            spec.args(fileName);
            if (getConfig().isPresent()) {
                spec.args(getConfig().get().getAsFile().getAbsolutePath());
            }
            spec.classpath(getFormatterClasspath());
            spec.getMainClass().set("dev.lukebemish.immaculate.eclipsejdtwrapper.Main");

            spec.setStandardInput(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
            spec.setStandardOutput(outputStream);
        });
        if (result.getExitValue() != 0) {
            System.out.println(outputStream.toString(StandardCharsets.UTF_8));
        }
        result.rethrowFailure().assertNormalExitValue();
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getConfig();
}
