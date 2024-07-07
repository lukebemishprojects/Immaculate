package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;

public abstract class AbstractFormattingStep extends FormattingStep {
    private final String name;

    @Inject
    public AbstractFormattingStep(String name) {
        this.name = name;
    }

    @Override
    @Input
    public String getName() {
        return name;
    }
}
