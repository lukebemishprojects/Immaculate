package dev.lukebemish.immaculate;

import dev.lukebemish.immaculate.steps.CustomStep;
import dev.lukebemish.immaculate.steps.EclipseJdtFormatStep;
import dev.lukebemish.immaculate.steps.GoogleJavaFormatStep;
import dev.lukebemish.immaculate.steps.ImportOrderStep;
import dev.lukebemish.immaculate.steps.LinewiseStep;
import dev.lukebemish.immaculate.steps.PalantirJavaFormatStep;
import org.gradle.api.Action;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import java.util.function.UnaryOperator;

public abstract class StepContainer {
    @Nested
    public NamedDomainObjectList<FormattingStep> getSteps() {
        return this.steps;
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
        steps.addLater(stepContainer.register(name, type, action));
    }

    public <T extends FormattingStep> void step(String name, Class<T> type) {
        step(name, type, it -> {});
    }

    @Inject
    public StepContainer() {}

    @Inject
    protected abstract ObjectFactory getObjects();

    @Internal
    private final transient Set<Class<?>> registeredBindings = new HashSet<>();

    @Internal
    private final NamedDomainObjectList<FormattingStep> steps = getObjects().namedDomainObjectList(FormattingStep.class);

    @Internal
    private final transient ExtensiblePolymorphicDomainObjectContainer<FormattingStep> stepContainer = getObjects().polymorphicDomainObjectContainer(FormattingStep.class);
}
