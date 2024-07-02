package dev.lukebemish.immaculate.steps;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;
import java.util.List;
import java.util.function.UnaryOperator;

public abstract class LinewiseStep extends AbstractFormattingStep {
    @Nested
    public abstract Property<UnaryOperator<String>> getAction();

    @Override
    public List<String> fix(String fileName, List<String> lines) {
        var operator = getAction().get();
        return lines.stream().map(l -> {
            var n = operator.apply(l);
            return n == null ? l : n;
        }).toList();
    }

    @Inject
    public LinewiseStep(String name) {
        super(name);
    }
}
