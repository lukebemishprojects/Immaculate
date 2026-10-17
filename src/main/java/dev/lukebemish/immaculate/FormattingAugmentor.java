package dev.lukebemish.immaculate;

import org.gradle.api.Named;
import org.gradle.api.tasks.Input;

public abstract class FormattingAugmentor extends StepContainer implements Named {
    public abstract FileAugmentor<?> augmentor();

    @Input
    public abstract String getName();

    public void workflow(FormattingWorkflow workflow) {
        getSteps().configureEach(step -> {
            step.workflow(workflow);
        });
    }
}
