package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.Formatter;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;

import javax.inject.Inject;

public abstract class ExternalFormattingStep extends AbstractFormattingStep {
    private transient final Formatter formatter;

    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public ExternalFormattingStep(String name, String workflowName, Project project, ObjectFactory objectFactory) {
        super(name);
        this.formatter = objectFactory.newInstance(Formatter.class);
        var configuration = project.getConfigurations().create("immaculate"+capitalize(workflowName)+capitalize(name)+"FormatterClasspath");
        configuration.fromDependencyCollector(formatter.getRuntime());
        getFormatterClasspath().from(configuration);
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
    public Formatter getFormatter() {
        return formatter;
    }

    public void formatter(Action<Formatter> action) {
        action.execute(getFormatter());
    }
}
