package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.List;

public abstract class NoTabsStep extends AbstractFormattingStep {
    @Inject
    public NoTabsStep(String name) {
        super(name);
    }

    @Input
    public abstract Property<Integer> getSpacesPerTab();

    @Override
    public List<String> fix(List<String> lines) {
        return lines.stream()
                .map(line -> line.replace("\t", " ".repeat(getSpacesPerTab().get())))
                .toList();
    }
}
