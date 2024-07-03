package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.FileFormatter;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;

public abstract class NoTabsStep extends AbstractFormattingStep {
    @Inject
    public NoTabsStep(String name) {
        super(name);
    }

    @Input
    public abstract Property<Integer> getSpacesPerTab();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> text.replace("\t", " ".repeat(getSpacesPerTab().get()));
    }
}
