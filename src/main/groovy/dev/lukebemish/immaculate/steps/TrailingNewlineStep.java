package dev.lukebemish.immaculate.steps;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class TrailingNewlineStep extends AbstractFormattingStep {
    @Inject
    public TrailingNewlineStep(String name) {
        super(name);
    }

    @Override
    public List<String> fix(String fileName, List<String> lines) {
        ArrayList<String> newLines = new ArrayList<>(lines);
        if (lines.isEmpty() || !lines.get(lines.size() - 1).isEmpty()) {
            newLines.add("");
        }
        return newLines;
    }
}
