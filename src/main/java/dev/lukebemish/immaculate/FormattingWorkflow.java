package dev.lukebemish.immaculate;

import dev.lukebemish.immaculate.steps.CustomStep;
import dev.lukebemish.immaculate.steps.EclipseJdtFormatStep;
import dev.lukebemish.immaculate.steps.GoogleJavaFormatStep;
import dev.lukebemish.immaculate.steps.ImportOrderStep;
import dev.lukebemish.immaculate.steps.LinewiseStep;
import dev.lukebemish.immaculate.steps.PalantirJavaFormatStep;
import org.gradle.api.Action;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.util.PatternFilterable;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import java.util.function.UnaryOperator;

public abstract class FormattingWorkflow implements Named {
    public NamedDomainObjectList<FormattingStep> getSteps() {
        return this.steps;
    }

    public abstract ConfigurableFileCollection getFiles();

    @Inject
    protected abstract Project getProject();

    public abstract Property<String> getToggleOff();
    public abstract Property<String> getToggleOn();
    public abstract Property<Boolean> getTruncateExceptions();
    public abstract RegularFileProperty getReportIssuesRootPath();

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

    public void linewise(String name, UnaryOperator<String> customAction) {
        step(name, LinewiseStep.class, it -> it.getAction().set(customAction));
    }

    public void trailingNewline() {
        custom("trailingNewline", it -> {
            if (!it.endsWith("\n") && !it.endsWith("\r\n")) {
                return it + "\n";
            }
            return it;
        });
    }

    public void noTabs(int spacesPerTab) {
        linewise("noTabs", it -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < it.length(); i++) {
                char c = it.charAt(i);
                if (c == '\t') {
                    sb.append(" ".repeat(spacesPerTab));
                } else if (c == ' ') {
                    sb.append(c);
                } else {
                    sb.append(it.substring(i));
                    break;
                }
            }
            return sb.toString();
        });
    }

    public void noTabs() {
        noTabs(4);
    }

    public void google() {
        google(it -> {});
    }

    public void google(Action<GoogleJavaFormatStep> action) {
        step("google", GoogleJavaFormatStep.class, action);
    }

    public void palantir() {
        palantir(it -> {});
    }

    public void palantir(Action<PalantirJavaFormatStep> action) {
        step("palantir", PalantirJavaFormatStep.class, action);
    }

    public void eclipse(Action<EclipseJdtFormatStep> action) {
        step("eclipse", EclipseJdtFormatStep.class, action);
    }

    public void noTrailingSpaces() {
        linewise("noTrailingSpaces", String::stripTrailing);
    }

    public void importOrder(Action<ImportOrderStep> action) {
        step("importOrder", ImportOrderStep.class, action);
    }

    public void custom(String name, UnaryOperator<String> customAction) {
        step(name, CustomStep.class, it -> it.getAction().set(customAction));
    }

    public <T extends FormattingStep> void step(String name, Class<T> type, Action<? super T> action) {
        if (registeredBindings.add(type)) {
            stepContainer.registerBinding(type, type);
        }
        steps.addLater(stepContainer.register(name, type, step -> {
            step.workflow(this);
            action.execute(step);
        }));
    }

    public <T extends FormattingStep> void step(String name, Class<T> type) {
        step(name, type, it -> {});
    }

    @Inject
    protected abstract ObjectFactory getObjects();

    private final Set<Class<?>> registeredBindings = new HashSet<>();

    private final NamedDomainObjectList<FormattingStep> steps = getObjects().namedDomainObjectList(FormattingStep.class);
    private final ExtensiblePolymorphicDomainObjectContainer<FormattingStep> stepContainer = getObjects().polymorphicDomainObjectContainer(FormattingStep.class);
}
