package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.UnaryOperator;

public abstract class CustomStep extends FormattingStep {
    @Nested
    public abstract Property<Function<String, @Nullable String>> getAction();

    @Override
    public FileFormatter formatter() {
        var operator = getAction().get();
        return (params) -> {
            var out = operator.apply(params.text());
            if (out == null) return params.text();
            return out;
        };
    }
}
