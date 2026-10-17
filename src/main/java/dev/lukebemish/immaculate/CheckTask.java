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
import org.gradle.api.problems.Problems;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationException;
import org.gradle.work.ChangeType;
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
import java.util.Date;
import java.util.List;
import java.util.stream.StreamSupport;

@CacheableTask
public abstract class CheckTask extends DefaultTask {
    static final ProblemGroup PROBLEM_GROUP = ProblemGroup.create("dev.lukebemish.immaculate", "Formatting Errors");
    private static final ProblemId FORMATTER_EXCEPTION =
        ProblemId.create("formatter-exception", "Exception thrown in formatter", PROBLEM_GROUP);
    private static final ProblemId AUGMENT_EXCEPTION =
        ProblemId.create("formatter-exception", "Exception thrown while applying augmentor", PROBLEM_GROUP);
    private static final ProblemId DEAUGMENT_EXCEPTION =
        ProblemId.create("formatter-exception", "Exception thrown while unapplying augmentor", PROBLEM_GROUP);
    private static final ProblemId FAILED_TO_CLOSE_FORMATTER =
        ProblemId.create("failed-to-close-formatter", "Failed to close formatter", PROBLEM_GROUP);
    private static final ProblemId FORMATTING_IO =
        ProblemId.create("formatting-io", "IO issue during formatting", PROBLEM_GROUP);

    @Nested
    public abstract ListProperty<FormattingStep> getSteps();

    @Nested
    public abstract ListProperty<FormattingAugmentor> getAugmentors();

    @InputFiles
    @Incremental
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getFiles();

    @Input
    public abstract Property<Boolean> getApplyFixes();

    @Internal
    public abstract DirectoryProperty getOldCopyDirectory();

    @Internal
    public abstract DirectoryProperty getReportIssuesRootPath();

    private record NamedFormatter(String name, FileFormatter formatter) {}

    private record NamedAugmentor<T>(String name, FileAugmentor<T> augmentor) {}

    private static final DateFormat OLD_FILE_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

    @TaskAction
    void execute(InputChanges inputs) throws IOException {
        if (getApplyFixes().get()) {
            FileUtils.deleteDirectory(getOldCopyDirectory().get().getAsFile());
        }
        final var reporter = new BetterProblemReporter(getProblems().getReporter());
        List<NamedFormatter> formatters = getSteps().get().stream().map(step -> new NamedFormatter(step.getName(), step.formatter())).toList();
        List<? extends NamedAugmentor<?>> augmentors = getAugmentors().get().stream().map(augmentor -> new NamedAugmentor<>(augmentor.getName(), augmentor.augmentor())).toList();
        try {
            StreamSupport.stream(inputs.getFileChanges(getFiles()).spliterator(), true).forEach(change -> {
                if (change.getChangeType() != ChangeType.REMOVED && change.getFileType() == FileType.FILE) {
                    try {
                        String originalText = Files.readString(change.getFile().toPath());
                        String text = originalText;

                        // Augment
                        Object[] augmentorData = new Object[augmentors.size()];
                        int idx = 0;
                        for (var augmentor : augmentors) {
                            String newText;
                            try {
                                final var fIdx = idx;
                                newText = augmentor.augmentor.augment(new FileParams(change.getFile().getName(), text), v -> {
                                    augmentorData[fIdx] = v;
                                });
                            } catch (Exception e) {
                                VerificationException ve;
                                if (e instanceof VerificationException verificationException) {
                                    ve = verificationException;
                                } else {
                                    ve = new VerificationException("Error in augmentor application", e);
                                }
                                reporter.throwingLater(
                                    ve,
                                    AUGMENT_EXCEPTION,
                                    spec -> {
                                        spec.contextualLabel("Error from augmentor '" + augmentor.name() + "'");
                                        spec.fileLocation(relativize(change.getFile()));
                                    });
                                throw new HandledException();
                            }
                            text = newText;
                            idx++;
                        }


                        boolean crlf = text.contains("\r\n");
                        var finalText = text;
                        for (var formatter : formatters) {
                            String newText;
                            try {
                                finalText = formatter.formatter.format(new FileParams(change.getFile().getName(), finalText));
                            } catch (Exception e) {
                                VerificationException ve;
                                if (e instanceof VerificationException verificationException) {
                                    ve = verificationException;
                                } else {
                                    ve = new VerificationException("Error in formatter step", e);
                                }
                                reporter.throwingLater(
                                    ve,
                                    FORMATTER_EXCEPTION,
                                    spec -> {
                                        spec.contextualLabel("Error from formatter '" + formatter.name() + "'");
                                        spec.fileLocation(relativize(change.getFile()));
                                    });
                                throw new HandledException();
                            }
                        }
                        finalText = finalText.replace("\r\n", "\n");
                        if (crlf) {
                            finalText = finalText.replace("\n", "\r\n");
                        }

                        // De-augment
                        idx = 0;
                        for (var augmentor : augmentors) {
                            try {
                                finalText = helpDeAugment(augmentor.augmentor, new FileParams(change.getFile().getName(), text), augmentorData[idx]);
                            } catch (Exception e) {
                                VerificationException ve;
                                if (e instanceof VerificationException verificationException) {
                                    ve = verificationException;
                                } else {
                                    ve = new VerificationException("Error in augmentor unapplication", e);
                                }
                                reporter.throwingLater(
                                    ve,
                                    DEAUGMENT_EXCEPTION,
                                    spec -> {
                                        spec.contextualLabel("Error from augmentor '" + augmentor.name() + "'");
                                        spec.fileLocation(relativize(change.getFile()));
                                    });
                                throw new HandledException();
                            }
                            idx++;
                        }

                        if (!originalText.equals(finalText)) {
                            if (getApplyFixes().get()) {
                                var timestamp = OLD_FILE_DATE_FORMAT.format(new Date());
                                var outFile = getOldCopyDirectory().file(change.getFile().getName() + "." + timestamp + DigestUtils.md5Hex(originalText));
                                Files.createDirectories(outFile.get().getAsFile().toPath().getParent());
                                Files.writeString(outFile.get().getAsFile().toPath(), originalText);
                                Files.writeString(change.getFile().toPath(), finalText);
                            } else {
                                new DiffProblem(change.getFile(), diff(originalText, finalText)).make(reporter, getReportIssuesRootPath().getOrNull());
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

    @SuppressWarnings("unchecked")
    private static <T> String helpDeAugment(FileAugmentor<T> augmentor, FileParams fileParams, Object augmentorDatum) {
        return augmentor.deAugment(fileParams, (T) augmentorDatum);
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

    record DiffData(String pretty, EditList edits) {}

    private static DiffData diff(String oldString, String newString) throws IOException {
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
        return new DiffData(out.toString(StandardCharsets.UTF_8), edits);
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
        this.getAugmentors().set(workflow.getAugmentors());
        this.getFiles().from(workflow.getFiles());
        getReportIssuesRootPath().set(workflow.getReportIssuesRootPath());
    }
}
