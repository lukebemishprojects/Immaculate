package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.ForkFormatterSpec;
import dev.lukebemish.immaculate.ImmaculatePlugin;
import dev.lukebemish.immaculate.ForkFormatter;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.jvm.toolchain.JavaLauncher;

import javax.inject.Inject;

public abstract class WrapperFormattingStep extends ExternalFormattingStep {
    public WrapperFormattingStep() {
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

    @Inject
    protected abstract LoggingConfiguration getLoggingConfiguration();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Override
    public FileFormatter formatter() {
        ForkFormatterSpec spec = getObjectFactory().newInstance(ForkFormatterSpec.class);
        configureSpec(spec);
        spec.getProgramArgs().addAll(getArgs());
        spec.getJvmArgs().addAll(getJvmArgs());
        spec.getJavaLauncher().set(getJavaLauncher());
        spec.getClasspath().from(getFormatterClasspath());
        spec.getHideStacktrace().set(getHideStacktrace().orElse(getLoggingConfiguration().getShowStacktrace() == ShowStacktrace.INTERNAL_EXCEPTIONS));
        return new ForkFormatter(spec);
    }

    @Input
    public abstract ListProperty<String> getArgs();

    @Input
    public abstract ListProperty<String> getJvmArgs();

    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();
}
