package dev.lukebemish.immaculate;

import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;

public abstract class FormattingAugmentor extends StepContainer implements Named {
    public abstract FileAugmentor<?> augmentor();

    @Override
    @Nested
    public NamedDomainObjectList<FormattingStep> getSteps() {
        return super.getSteps();
    }

    @Inject
    public FormattingAugmentor() {}

    @Input
    public abstract String getName();

    public void workflow(FormattingWorkflow workflow) {
        getSteps().configureEach(step -> {
            step.workflow(workflow);
        });
    }
}
