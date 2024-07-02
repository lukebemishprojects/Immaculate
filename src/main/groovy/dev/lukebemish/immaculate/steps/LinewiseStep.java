package dev.lukebemish.immaculate.steps;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public abstract class LinewiseStep extends AbstractFormattingStep {
    @Nested
    public abstract Property<UnaryOperator<String>> getAction();

    @Override
    public String fix(String fileName, String text) {
        var operator = getAction().get();
        var lines = text.split("((\r\n)|\n)", -1);
        return Arrays.stream(lines).map(l -> {
            var n = operator.apply(l);
            return n == null ? l : n;
        }).collect(Collectors.joining("\n"));
    }

    @Inject
    public LinewiseStep(String name) {
        super(name);
    }
}
