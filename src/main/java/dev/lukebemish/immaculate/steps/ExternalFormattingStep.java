package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.FormatterDependencies;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;

import javax.inject.Inject;

public abstract class ExternalFormattingStep extends AbstractFormattingStep {
    private transient final FormatterDependencies formatterDependencies;

    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public ExternalFormattingStep(String name, String workflowName, Project project, ObjectFactory objectFactory) {
        super(name);
        this.formatterDependencies = objectFactory.newInstance(FormatterDependencies.class);
        // We have to use this as some plugins (*ahem* NeoGradle) screw up lazy use of DependencyCollector
        // TODO: move this back to normal configurations
        var detached = project.getConfigurations().detachedConfiguration();
        detached.fromDependencyCollector(formatterDependencies.getRuntime());
        getFormatterClasspath().from(detached);
    }

    private String capitalize(String str) {
        if (str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getFormatterClasspath();

    @Internal
    public FormatterDependencies getFormatter() {
        return formatterDependencies;
    }

    public void formatter(Action<FormatterDependencies> action) {
        action.execute(getFormatter());
    }
}