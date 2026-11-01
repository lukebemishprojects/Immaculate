package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class LinewiseStep extends FormattingStep {
    @Nested
    public abstract Property<Function<String, @Nullable String>> getAction();

    @Override
    public FileFormatter formatter() {
        return (params) -> {
            var operator = getAction().get();
            var lines = params.text().split("((\r\n)|\n)", -1);
            return Arrays.stream(lines).map(l -> {
                var n = operator.apply(l);
                return n == null ? l : n;
            }).collect(Collectors.joining("\n"));
        };
    }
}
