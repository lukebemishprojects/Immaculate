package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.FileFormatter;

import javax.inject.Inject;

public abstract class TrailingNewlineStep extends AbstractFormattingStep {
    @Inject
    public TrailingNewlineStep(String name) {
        super(name);
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> {
            if (!text.endsWith("\n") && !text.endsWith("\r\n")) {
                return text + "\n";
            }
            return text;
        };
    }
}
