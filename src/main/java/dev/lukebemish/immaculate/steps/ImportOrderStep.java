package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.FileFormatter;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ImportOrderStep extends AbstractFormattingStep {
    @InputFile
    public abstract RegularFileProperty getConfig();

    @Inject
    public ImportOrderStep(String name) {
        super(name);
    }

    @Override
    public FileFormatter formatter() {
        List<String> ordering;

        record SingleEntry(int key, String name) {}

        try (Stream<String> lines = Files.lines(getConfig().get().getAsFile().toPath())) {
            ordering = lines.filter(line -> !line.startsWith("#"))
                .map(line -> {
                    String[] pieces = line.split("=");
                    int index = Integer.parseInt(pieces[0]);
                    String name = pieces.length == 2 ? pieces[1] : "";
                    if (!name.isEmpty() && !name.endsWith(".")) {
                        name += ".";
                    }
                    return new SingleEntry(index, name);
                })
                .sorted(Comparator.comparing(SingleEntry::key))
                .map(SingleEntry::name)
                .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        var pattern = Pattern.compile("^import\\s+([^\\s;]+)");

        return (fileName, text) -> {
            var scanner = new Scanner(text);
            int firstImportLine = -1;
            int lastImportLine = -1;
            int lineNumber = -1;
            boolean isMultiLineComment = false;

            record ImportInfo(String importSection, String commentSection) {}

            List<ImportInfo> imports = new ArrayList<>();

            while (scanner.hasNext()) {
                lineNumber++;
                String line = scanner.nextLine();

                String rest = line;
                int idx = 0;
                boolean multilineCommentPart = false;
                while ((rest.contains("/*") && !isMultiLineComment) || (rest.contains("*/") && isMultiLineComment)) {
                    multilineCommentPart = true;
                    if (isMultiLineComment) {
                        var closing = line.indexOf("*/", idx);
                        if (closing != -1) {
                            isMultiLineComment = false;
                            rest = rest.substring(closing + 2);
                        }
                    } else {
                        var opening = line.indexOf("/*", idx);
                        if (opening != -1) {
                            isMultiLineComment = true;
                            rest = rest.substring(opening + 2);
                        }
                    }
                }

                if (multilineCommentPart) {
                    // We cannot wrap imports around a comment
                    if (firstImportLine >= 0) {
                        break;
                    }
                    continue;
                }

                if (line.startsWith("import ")) {
                    var match = pattern.matcher(line);
                    if (match.find()) {
                        if (firstImportLine < 0) {
                            firstImportLine = lineNumber;
                        }
                        lastImportLine = lineNumber;
                        var string = match.group(1);
                        var end = match.end();
                        imports.add(new ImportInfo(string, line.substring(end)));
                    }
                } else if (!line.isBlank()) {
                    // If there is a non-blank line, we consider the imports to be complete
                    if (firstImportLine >= 0) {
                        break;
                    }
                }
            }

            scanner.close();

            var groups = imports.stream().collect(Collectors.groupingBy(info -> {
                int longestMatch = -1;
                int longestMatchIdx = ordering.size();
                for (int i = 0; i < ordering.size(); i++) {
                    var singlePattern = ordering.get(i);
                    if (singlePattern.length() > longestMatch) {
                        if (info.importSection.startsWith(singlePattern)) {
                            longestMatch = singlePattern.length();
                            longestMatchIdx = i;
                        }
                    }
                }
                return longestMatchIdx;
            })).entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).filter(l -> !l.isEmpty()).map(list ->
                list.stream().sorted(Comparator.comparing(ImportInfo::importSection, (o1, o2) -> {
                    var parts1 = o1.split("\\.");
                    var parts2 = o2.split("\\.");
                    int minLength = Math.min(parts1.length, parts2.length);
                    for (int i = 0; i < minLength; i++) {
                        int cmp = parts1[i].trim().compareTo(parts2[i].trim());
                        if (cmp != 0) {
                            return cmp;
                        }
                    }
                    return Integer.compare(parts1.length, parts2.length);
                })).toList()
            ).toList();

            var out = new StringBuilder();
            scanner = new Scanner(text);
            lineNumber = -1;
            while (scanner.hasNext()) {
                lineNumber++;
                var line = scanner.nextLine();

                if (lineNumber < firstImportLine || lineNumber > lastImportLine) {
                    if (lineNumber > 0) {
                        out.append('\n');
                    }
                    out.append(line);
                } else if (lineNumber == firstImportLine) {
                    if (lineNumber > 0) {
                        out.append('\n');
                    }
                    out.append(groups.stream().map(list -> list.stream().map(info -> "import "+info.importSection+info.commentSection).collect(Collectors.joining("\n")))
                        .collect(Collectors.joining("\n\n")));
                }
            }

            return out.toString();
        };
    }
}
