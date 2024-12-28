package dev.lukebemish.immaculate;

import dev.lukebemish.immaculate.steps.CustomStep;
import dev.lukebemish.immaculate.steps.EclipseJdtFormatStep;
import dev.lukebemish.immaculate.steps.GoogleJavaFormatStep;
import dev.lukebemish.immaculate.steps.LinewiseStep;
import dev.lukebemish.immaculate.steps.PalantirJavaFormatStep;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSetContainer;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public void java() {
        SourceSetContainer sourceSets = getProject().getExtensions().getByType(SourceSetContainer.class);
        ConfigurableFileCollection sourceDirs = getProject().files();
        sourceSets.configureEach(it -> sourceDirs.from(it.getAllSource()));

        getFiles().from(sourceDirs.filter(it -> it.getName().endsWith(".java")));
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

    public void googleRemoveUnusedImports() {
        step("googleRemoveUnusedImports", GoogleJavaFormatStep.class, it -> it.getArgs().addAll("--fix-imports-only", "--skip-sorting-imports"));
    }

    public void googleSortImports() {
        step("googleSortImports", GoogleJavaFormatStep.class, it -> it.getArgs().addAll("--fix-imports-only", "--skip-removing-unused-imports"));
    }

    public void googleFixImports() {
        step("googleFixImports", GoogleJavaFormatStep.class, it -> it.getArgs().addAll("--fix-imports-only"));
    }

    public void google() {
        step("google", GoogleJavaFormatStep.class);
    }

    public void google(Action<GoogleJavaFormatStep> action) {
        step("google", GoogleJavaFormatStep.class, action);
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

    public void custom(String name, UnaryOperator<String> customAction) {
        step(name, CustomStep.class, it -> it.getAction().set(customAction));
    }

    public <T extends FormattingStep> void step(String name, Class<T> type, Action<? super T> action) {
        steps.addLater(getProject().provider(() -> {
            T step = createStep(type, name);
            action.execute(step);
            return step;
        }));
    }

    public <T extends FormattingStep> void step(String name, Class<T> type) {
        step(name, type, it -> {});
    }

    private final Map<Class<? extends FormattingStep>, NamedDomainObjectFactory<? extends FormattingStep>> factories = new HashMap<>();

    public FormattingWorkflow() {
        for (Class<? extends FormattingStep> clazz : List.of(
            LinewiseStep.class, CustomStep.class
        )) {
            registerStepType(clazz);
        }
        for (Class<? extends FormattingStep> clazz : List.of(
            GoogleJavaFormatStep.class, EclipseJdtFormatStep.class, PalantirJavaFormatStep.class
        )) {
            registerFormatterStepType(clazz);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends FormattingStep> T createStep(Class<T> clazz, String name) {
        NamedDomainObjectFactory<T> factory = (NamedDomainObjectFactory<T>) factories.get(clazz);
        if (factory == null) {
            throw new IllegalArgumentException("No factory for step type "+clazz);
        }
        return factory.create(name);
    }

    @Inject
    protected abstract ObjectFactory getObjects();

    private final NamedDomainObjectList<FormattingStep> steps = getObjects().namedDomainObjectList(FormattingStep.class);

    private <T extends FormattingStep> void registerStepType(Class<T> clazz) {
        factories.put(clazz, name -> getObjects().newInstance(clazz, name));
    }

    private <T extends FormattingStep> void registerFormatterStepType(Class<T> clazz) {
        factories.put(clazz, name -> getObjects().newInstance(clazz, name, this.getName()));
    }
}
