package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;
import java.util.function.UnaryOperator;

public abstract class CustomStep extends FormattingStep {
    @Nested
    public abstract Property<UnaryOperator<String>> getAction();

    @Override
    public FileFormatter formatter() {
        var operator = getAction().get();
        return (fileName, text) -> operator.apply(text);
    }
}
