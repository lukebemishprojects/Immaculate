package dev.lukebemish.immaculate.augmentors;

import dev.lukebemish.immaculate.FileAugmentor;
import dev.lukebemish.immaculate.FileParams;
import dev.lukebemish.immaculate.FormattingAugmentor;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class ToggleAugmentor extends FormattingAugmentor {
    @Input
    public abstract Property<String> getOff();
    @Input
    public abstract Property<String> getOn();

    @Override
    public FileAugmentor<?> augmentor() {
        var toggleOn = getOn().get();
        var toggleOff = getOff().get();
        return new FileAugmentor<List<String>>() {
            @Override
            public String augment(FileParams params, Consumer<List<String>> store) {
                int index = 0;
                List<String> parts = new ArrayList<>();
                List<String> excluded = new ArrayList<>();

                var text = params.text();
                while (index < text.length()) {
                    int oldIndex = index;
                    index = text.indexOf(toggleOff, index);
                    if (index != -1) {
                        int end = text.indexOf(toggleOn, index);
                        if (end != -1) {
                            int startIndex = index + toggleOff.length();
                            int nextIndex = end + toggleOn.length();
                            String suffix = ":" + excluded.size();
                            parts.add(text.substring(oldIndex, startIndex) + suffix);
                            excluded.add(text.substring(startIndex, nextIndex));
                            parts.add(text.substring(startIndex, nextIndex) + suffix);
                            index = nextIndex;
                        } else {
                            throw new RuntimeException("Mismatched toggle");
                        }
                    } else {
                        parts.add(text.substring(oldIndex));
                        break;
                    }
                }
                text = String.join("", parts);

                store.accept(excluded);

                return text;
            }

            @Override
            public String deAugment(FileParams params, List<String> excluded) {
                var text = params.text();

                for (int i = 0; i < excluded.size(); i++) {
                    int start = text.indexOf(toggleOff + ':' + i);
                    if (start == -1) {
                        throw new RuntimeException("Could not recover toggle block");
                    }
                    int end = text.indexOf(toggleOn + ':' + i, start);
                    if (end == -1) {
                        throw new RuntimeException("Could not recover toggle block");
                    }
                    int startIndex = start + toggleOff.length() + (":" + i).length();
                    int nextIndex = end + toggleOn.length() + (":" + i).length();
                    String inner = text.substring(startIndex, end);
                    if (inner.contains(toggleOff) || inner.contains(toggleOn)) {
                        throw new RuntimeException("Mismatched toggle");
                    }
                    text = text.substring(0, start) + toggleOff + excluded.get(i) + text.substring(nextIndex);
                }

                return text;
            }
        };
    }
}
