package dev.lukebemish.immaculate;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLauncher;

import javax.inject.Inject;

public abstract class ForkFormatterSpec {
    public abstract Property<JavaLauncher> getJavaLauncher();

    public abstract ConfigurableFileCollection getClasspath();

    public abstract Property<String> getWrapperClass();

    public abstract Property<Boolean> getHideStacktrace();

    public abstract Property<String> getWrapperMainClass();

    public abstract ListProperty<String> getJvmArgs();

    public abstract ListProperty<String> getProgramArgs();

    @Inject
    public ForkFormatterSpec() {
        getWrapperMainClass().convention("dev.lukebemish.immaculate.wrapper.Main");
    }
}
