package dev.lukebemish.immaculate;

import dev.lukebemish.immaculate.augmentors.ToggleAugmentor;
import org.gradle.api.Action;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.util.PatternFilterable;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;

public abstract class FormattingWorkflow extends StepContainer implements Named {
    public NamedDomainObjectList<FormattingAugmentor> getAugmentors() {
        return this.augmentors;
    }

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

    public void toggle(Action<? super ToggleAugmentor> action) {
        augmentor("toggle", ToggleAugmentor.class, action);
    }

    public <T extends FormattingAugmentor> void augmentor(String name, Class<T> type, Action<? super T> action) {
        if (registeredBindings.add(type)) {
            augmentorContainer.registerBinding(type, type);
        }
        augmentors.addLater(augmentorContainer.register(name, type, action));
    }

    public <T extends FormattingAugmentor> void augmentor(String name, Class<T> type) {
        augmentor(name, type, it -> {});
    }

    @Inject
    public FormattingWorkflow() {
        getSteps().configureEach(step -> {
            step.workflow(this);
        });
        getAugmentors().configureEach(augmentor -> {
            augmentor.workflow(this);
        });
    }

    @Inject
    protected abstract ObjectFactory getObjects();

    @Internal
    private final Set<Class<?>> registeredBindings = new HashSet<>();

    @Internal
    private final NamedDomainObjectList<FormattingAugmentor> augmentors = getObjects().namedDomainObjectList(FormattingAugmentor.class);

    @Internal
    private final ExtensiblePolymorphicDomainObjectContainer<FormattingAugmentor> augmentorContainer = getObjects().polymorphicDomainObjectContainer(FormattingAugmentor.class);
}
