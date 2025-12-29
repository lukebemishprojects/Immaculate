package dev.lukebemish.immaculate;

import org.gradle.api.Named;
import org.gradle.api.tasks.Input;

public abstract class FormattingStep implements Named {
    public abstract FileFormatter formatter();

    @Input
    public abstract String getName();

    public void workflow(FormattingWorkflow workflow) {}
}
