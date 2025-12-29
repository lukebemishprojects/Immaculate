package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.FormatterDependencies;
import dev.lukebemish.immaculate.FormattingStep;
import dev.lukebemish.immaculate.FormattingWorkflow;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;

import javax.inject.Inject;
import java.util.Locale;

public abstract class ExternalFormattingStep extends FormattingStep {
    private transient final FormatterDependencies formatterDependencies;

    @Inject
    protected abstract Project getProject();

    @Inject
    public ExternalFormattingStep() {
        this.formatterDependencies = getProject().getObjects().newInstance(FormatterDependencies.class);
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void workflow(FormattingWorkflow workflow) {
        var runtime = getProject().getConfigurations().create("immaculate" + capitalize(workflow.getName()) + capitalize(getName()) + "Runtime");
        runtime.fromDependencyCollector(formatterDependencies.getRuntime());
        getFormatterClasspath().from(runtime);
    }

    private String capitalize(String str) {
        if (str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
    }

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getFormatterClasspath();

    @Internal
    public FormatterDependencies getDependencies() {
        return formatterDependencies;
    }

    public void dependencies(Action<FormatterDependencies> action) {
        action.execute(getDependencies());
    }
}
