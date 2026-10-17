package dev.lukebemish.immaculate;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileType;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Problems;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationException;
import org.gradle.work.ChangeType;
import org.gradle.work.FileChange;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.StreamSupport;

@CacheableTask
public abstract class CheckTask extends DefaultTask {
    static final ProblemGroup PROBLEM_GROUP = ProblemGroup.create("dev.lukebemish.immaculate", "Formatting Errors");
    private static final ProblemId EXCEPTION =
        ProblemId.create("exception-thrown", "Exception thrown in formatter", PROBLEM_GROUP);
    private static final ProblemId FAILED_TO_CLOSE_FORMATTER =
        ProblemId.create("failed-to-close-formatter", "Failed to close formatter", PROBLEM_GROUP);
    private static final ProblemId FORMATTING_IO =
        ProblemId.create("formatting-io", "IO issue during formatting", PROBLEM_GROUP);

    @Nested
    public abstract ListProperty<FormattingStep> getSteps();

    @InputFiles
    @Incremental
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getFiles();

    @Input
    public abstract Property<Boolean> getApplyFixes();

    @Internal
    public abstract DirectoryProperty getOldCopyDirectory();

    @Input
    @Optional
    public abstract Property<String> getToggleOff();

    @Input
    @Optional
    public abstract Property<String> getToggleOn();

    @Internal
    public abstract Property<Boolean> getTruncateExceptions();

    @Internal
    public abstract DirectoryProperty getReportIssuesRootPath();

    private record NamedFormatter(String name, FileFormatter formatter) {}

    private static final DateFormat OLD_FILE_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

    @TaskAction
    void execute(InputChanges inputs) throws IOException {
        if (getApplyFixes().get()) {
            FileUtils.deleteDirectory(getOldCopyDirectory().get().getAsFile());
        }
        final var reporter = new BetterProblemReporter(getProblems().getReporter());
        List<NamedFormatter> formatters = getSteps().get().stream().map(step -> new NamedFormatter(step.getName(), step.formatter())).toList();
        try {
            StreamSupport.stream(inputs.getFileChanges(getFiles()).spliterator(), true).forEach(change -> {
                if (change.getChangeType() != ChangeType.REMOVED && change.getFileType() == FileType.FILE) {
                    try {
                        String originalText = Files.readString(change.getFile().toPath());
                        String text = originalText;

                        // Grab toggles
                        int index = 0;
                        List<String> parts = new ArrayList<>();
                        List<String> excluded = new ArrayList<>();
                        if (getToggleOff().isPresent() && getToggleOn().isPresent()) {
                            while (index < text.length()) {
                                int oldIndex = index;
                                index = text.indexOf(getToggleOff().get(), index);
                                if (index != -1) {
                                    int end = text.indexOf(getToggleOn().get(), index);
                                    if (end != -1) {
                                        int startIndex = index + getToggleOff().get().length();
                                        int nextIndex = end + getToggleOn().get().length();
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
                        }


                        boolean crlf = text.contains("\r\n");
                        final var finalText = new String[]{text};
                        for (var formatter : formatters) {
                            String newText;
                            try {
                                newText = formatter.formatter.format(new FileParams(change.getFile().getName(), finalText[0]));
                            } catch (Exception e) {
                                VerificationException ve;
                                if (e instanceof VerificationException verificationException) {
                                    ve = verificationException;
                                } else {
                                    ve = new VerificationException("Error in formatter step", e);
                                }
                                reporter.throwingLater(
                                    ve,
                                    EXCEPTION,
                                    spec -> {
                                        spec.contextualLabel("Error from formatter '" + formatter.name() + "'");
                                        spec.fileLocation(relativize(change.getFile()));
                                    });
                                throw new HandledException();
                            }
                            if (!finalText[0].equals(newText)) {
                                finalText[0] = newText;
                            }
                        }
                        finalText[0] = finalText[0].replace("\r\n", "\n");
                        if (crlf) {
                            finalText[0] = finalText[0].replace("\n", "\r\n");
                        }

                        // Replace toggles
                        if (getToggleOff().isPresent() && getToggleOn().isPresent()) {
                            for (int i = 0; i < excluded.size(); i++) {
                                int start = finalText[0].indexOf(getToggleOff().get() + ':' + i);
                                if (start == -1) {
                                    throw new RuntimeException("Could not recover toggle block");
                                }
                                int end = finalText[0].indexOf(getToggleOn().get() + ':' + i, start);
                                if (end == -1) {
                                    throw new RuntimeException("Could not recover toggle block");
                                }
                                int startIndex = start + getToggleOff().get().length() + (":" + i).length();
                                int nextIndex = end + getToggleOn().get().length() + (":" + i).length();
                                String inner = finalText[0].substring(startIndex, end);
                                if (inner.contains(getToggleOff().get()) || inner.contains(getToggleOn().get())) {
                                    throw new RuntimeException("Mismatched toggle");
                                }
                                finalText[0] = finalText[0].substring(0, start) + getToggleOff().get() + excluded.get(i) + finalText[0].substring(nextIndex);
                            }
                        }

                        if (!originalText.equals(finalText[0])) {
                            if (getApplyFixes().get()) {
                                var timestamp = OLD_FILE_DATE_FORMAT.format(new Date());
                                var outFile = getOldCopyDirectory().file(change.getFile().getName() + "." + timestamp + DigestUtils.md5Hex(originalText));
                                Files.createDirectories(outFile.get().getAsFile().toPath().getParent());
                                Files.writeString(outFile.get().getAsFile().toPath(), originalText);
                                Files.writeString(change.getFile().toPath(), finalText[0]);
                            } else {
                                new DiffProblem(change.getFile(), diff(originalText, finalText[0])).make(reporter, getReportIssuesRootPath().getOrNull());
                                throw new HandledException();
                            }
                        }
                    } catch (IOException e) {
                        // Fail-fast -- this is generally speaking _bad_
                        throw reporter.throwing(
                            e,
                            FORMATTING_IO,
                            spec -> {
                                spec.fileLocation(relativize(change.getFile()));
                            }
                        );
                    } catch (HandledException e) {}
                }
            });
        } finally {
            for (NamedFormatter formatter : formatters) {
                try {
                    formatter.formatter.close();
                } catch (Exception e) {
                    reporter.throwingLater(
                        e,
                        FAILED_TO_CLOSE_FORMATTER,
                        spec -> {
                            spec.contextualLabel("Error from formatter '" + formatter.name() + "'");
                        }
                    );
                }
            }
            reporter.flush(this);
        }
    }

    private String relativize(File file) {
        var rootPath = getReportIssuesRootPath().getOrNull();
        if (rootPath == null) {
            return file.getPath();
        } else {
            return rootPath.getAsFile().toPath().relativize(file.toPath()).toString();
        }
    }

    private static class HandledException extends Exception {}

    @Inject
    protected abstract LoggingConfiguration getLoggingConfiguration();

    @Inject
    protected abstract Problems getProblems();

    private static String diff(String oldString, String newString) throws IOException {
        RawText oldText = new RawText(makeVisible(oldString).getBytes(StandardCharsets.UTF_8));
        RawText newText = new RawText(makeVisible(newString).getBytes(StandardCharsets.UTF_8));
        EditList edits = new EditList();
        edits.addAll(MyersDiff.INSTANCE.diff(
            RawTextComparator.DEFAULT,
            oldText,
            newText
        ));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.format(edits, oldText, newText);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String makeVisible(String text) {
        return text.replace(' ', '·').replace("\t", "\\t").replace("\r", "");
    }

    public CheckTask() {
        getOutputs().upToDateWhen(t -> true);
        getApplyFixes().convention(false);
        getOldCopyDirectory().convention(getProject().getLayout().getBuildDirectory().dir("immaculate/"+getName()));
    }

    protected void from(FormattingWorkflow workflow) {
        this.getSteps().set(workflow.getSteps());
        this.getFiles().from(workflow.getFiles());
        this.getToggleOff().set(workflow.getToggleOff());
        this.getToggleOn().set(workflow.getToggleOn());
        getTruncateExceptions().set(workflow.getTruncateExceptions());
        getReportIssuesRootPath().set(workflow.getReportIssuesRootPath());
    }
}
