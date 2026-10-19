package dev.lukebemish.immaculate.augmentors;

import dev.lukebemish.immaculate.FileAugmentor;
import dev.lukebemish.immaculate.FileParams;
import dev.lukebemish.immaculate.FormattingAugmentor;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public abstract class IgnoreAugmentor extends FormattingAugmentor {
    @Input
    public abstract Property<String> getTag();

    @Input
    public abstract Property<String> getTemplate();

    @Input
    public abstract Property<String> getTemplatePattern();

    @Inject
    public IgnoreAugmentor() {
        getTag().convention("immaculate:ignore");
        getTemplate().convention("//%s");
        getTemplatePattern().convention("//\\s*%s");
    }

    @Override
    public FileAugmentor<?> augmentor() {
        var tag = getTag().get();
        var template = getTemplate().get();
        var templatePattern = getTemplatePattern().get();
        return new FileAugmentor<List<String>>() {
            @Override
            public String augment(FileParams params, Consumer<List<String>> store) {
                var excluded = new ArrayList<String>();
                var lines = params.text().lines().iterator();
                var outLines = new ArrayList<String>();
                int idx = 0;
                while (lines.hasNext()) {
                    var line = lines.next();
                    if (line.contains(tag)) {
                        var indent = calculateIndent(line);
                        outLines.add(indent + String.format(template, tag+":"+idx));
                        excluded.add(line.substring(indent.length()));
                        idx++;
                    } else {
                        outLines.add(line);
                    }
                }
                store.accept(excluded);
                return String.join("\n", outLines) + (params.text().endsWith("\n") ? "\n" : "");
            }

            private static final Pattern INDENT = Pattern.compile("^\\s*");
            private static final Pattern INT = Pattern.compile("^[0-9]+");

            private String calculateIndent(String line) {
                var match = INDENT.matcher(line);
                match.find();
                return match.group();
            }

            @Override
            public String deAugment(FileParams params, List<String> value) {
                var lines = params.text().lines().iterator();
                var outLines = new ArrayList<String>();
                var found = new boolean[value.size()];
                while (lines.hasNext()) {
                    var line = lines.next();
                    if (line.contains(tag+":")) {
                        var idx = line.indexOf(tag+":");
                        var after = line.substring(idx + (tag+":").length());
                        var idxMatch = INT.matcher(after);
                        idxMatch.find();
                        var idxStr = idxMatch.group(0);
                        var wholeTagMatch = Pattern.compile(String.format(templatePattern, Pattern.quote(tag+":"+idxStr))+"$")
                            .matcher(line);
                        wholeTagMatch.find();
                        var wholeTag = wholeTagMatch.group(0);
                        var before = line.substring(0, line.length() - wholeTag.length());
                        var iIdx = Integer.parseInt(idxStr);
                        outLines.add(before + value.get(iIdx));
                        found[iIdx] = true;
                    } else {
                        outLines.add(line);
                    }
                }
                for (int i = 0; i < value.size(); i++) {
                    if (!found[i]) {
                        throw new RuntimeException("Lost tag '"+tag+":"+i+"'");
                    }
                }
                return String.join("\n", outLines) + (params.text().endsWith("\n") ? "\n" : "");
            }
        };
    }
}
