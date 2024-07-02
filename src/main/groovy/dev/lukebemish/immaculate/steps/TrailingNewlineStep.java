package dev.lukebemish.immaculate.steps;

import javax.inject.Inject;

public abstract class TrailingNewlineStep extends AbstractFormattingStep {
    @Inject
    public TrailingNewlineStep(String name) {
        super(name);
    }

    @Override
    public String fix(String fileName, String text) {
        if (!text.endsWith("\n") && !text.endsWith("\r\n")) {
            return text + "\n";
        }
        return text;
    }
}
