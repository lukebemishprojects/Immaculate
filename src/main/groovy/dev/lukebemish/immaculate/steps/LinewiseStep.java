package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;
import javax.naming.spi.ObjectFactory;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

public abstract class LinewiseStep extends AbstractFormattingStep {
    @Nested
    public abstract Property<UnaryOperator<String>> getAction();

    @Override
    public List<String> fix(List<String> lines) {
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
