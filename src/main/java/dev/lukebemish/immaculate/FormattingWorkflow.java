package dev.lukebemish.immaculate;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.util.PatternFilterable;

import javax.inject.Inject;

public abstract class FormattingWorkflow extends StepContainer implements Named {
    public abstract ConfigurableFileCollection getFiles();

    @Inject
    protected abstract Project getProject();

    public abstract DirectoryProperty getReportIssuesRootPath();

    public void java() {
        java(it -> {});
    }

    public void java(Action<PatternFilterable> action) {
        ConfigurableFileCollection sourceFiles = getSourceFiles(action);

        getFiles().from(sourceFiles.filter(it -> it.getName().endsWith(".java")));
    }

    private ConfigurableFileCollection getSourceFiles(Action<PatternFilterable> action) {
        SourceSetContainer sourceSets = getProject().getExtensions().getByType(SourceSetContainer.class);
        ConfigurableFileCollection sourceFiles = getProject().files();
        sourceSets.configureEach(it -> {
            var tree = it.getAllSource().matching(action);
            sourceFiles.from(tree);
        });
        return sourceFiles;
    }

    public void sources() {
        sources(it -> {});
    }

    public void sources(Action<PatternFilterable> action) {
        ConfigurableFileCollection sourceFiles = getSourceFiles(action);

        getFiles().from(sourceFiles);
    }

    @Inject
    public FormattingWorkflow() {}

    @Inject
    protected abstract ObjectFactory getObjects();
}
