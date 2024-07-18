package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.ForkFormatterSpec;
import dev.lukebemish.immaculate.ImmaculatePlugin;
import dev.lukebemish.immaculate.ForkFormatter;
import org.gradle.api.Project;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

public abstract class WrapperFormattingStep extends ExternalFormattingStep {
    private final ObjectFactory objectFactory;

    @SuppressWarnings("UnstableApiUsage")
    public WrapperFormattingStep(String name, String workflowName, Project project, ObjectFactory objectFactory, JavaToolchainService javaToolchainService) {
        super(name, workflowName, project, objectFactory);
        getJavaLauncher().convention(project.provider(() -> {
            var java = project.getExtensions().findByType(JavaPluginExtension.class);
            if (java != null) {
                return java.getToolchain();
            }
            return null;
        }).flatMap(javaToolchainService::launcherFor).orElse(javaToolchainService.launcherFor(spec -> {})));
        getJavaLauncher().convention(javaToolchainService.launcherFor(spec -> {}));
        this.objectFactory = objectFactory;
        getHideStacktrace().convention(project.getGradle().getStartParameter().getShowStacktrace() == ShowStacktrace.INTERNAL_EXCEPTIONS);
        this.getDependencies().getRuntime().add("dev.lukebemish.immaculate:wrapper", dep -> {
            if (ImmaculatePlugin.PLUGIN_VERSION != null) {
                dep.version(constraint ->
                    constraint.require(ImmaculatePlugin.PLUGIN_VERSION)
                );
            }
        });
    }

    @Internal
    protected abstract Property<Boolean> getHideStacktrace();

    abstract protected void configureSpec(ForkFormatterSpec spec);

    @Override
    public FileFormatter formatter() {
        ForkFormatterSpec spec = objectFactory.newInstance(ForkFormatterSpec.class);
        configureSpec(spec);
        spec.getProgramArgs().addAll(getArgs());
        spec.getJvmArgs().addAll(getJvmArgs());
        spec.getJavaLauncher().set(getJavaLauncher());
        spec.getClasspath().from(getFormatterClasspath());
        spec.getHideStacktrace().set(getHideStacktrace());
        return new ForkFormatter(spec);
    }

    @Input
    public abstract ListProperty<String> getArgs();

    @Input
    public abstract ListProperty<String> getJvmArgs();

    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();
}
