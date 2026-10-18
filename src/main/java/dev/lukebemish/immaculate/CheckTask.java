package dev.lukebemish.immaculate;

import dev.lukebemish.immaculate.steps.AugmentorStep;
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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Problems;
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
import org.gradle.work.ChangeType;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

@CacheableTask
public abstract class CheckTask extends DefaultTask {
    @ApiStatus.Internal
    public static final ProblemGroup PROBLEM_GROUP = ProblemGroup.create("dev.lukebemish.immaculate", "Formatting Errors");
    private static final ProblemId FORMATTING_IO =
        ProblemId.create("formatting-io", "IO issue during formatting", PROBLEM_GROUP);

    @Nested
    public abstract Property<FormattingStep> getFormattingStep();

    @Internal
    protected abstract Property<String> getLinkedApplyTaskPath();

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

    private static final DateFormat OLD_FILE_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

    @TaskAction
    void execute(InputChanges inputs) throws IOException {
        if (getApplyFixes().get()) {
            FileUtils.deleteDirectory(getOldCopyDirectory().get().getAsFile());
        }

        var formatter = getFormattingStep().get().formatter();

        final var reporter = new ProblemReportingHelper(getProblems().getReporter());
        try {
            StreamSupport.stream(inputs.getFileChanges(getFiles()).spliterator(), true).forEach(change -> {
                if (change.getChangeType() != ChangeType.REMOVED && change.getFileType() == FileType.FILE) {
                    try {
                        String originalText = Files.readString(change.getFile().toPath());
                        var finalText = formatter.format(
                            new FileParams(change.getFile().getName(), relativize(change.getFile()), originalText, reporter)
                        );

                        if (!originalText.equals(finalText)) {
                            if (getApplyFixes().get()) {
                                var timestamp = OLD_FILE_DATE_FORMAT.format(new Date());
                                var outFile = getOldCopyDirectory().file(change.getFile().getName() + "." + timestamp + DigestUtils.md5Hex(originalText));
                                Files.createDirectories(outFile.get().getAsFile().toPath().getParent());
                                Files.writeString(outFile.get().getAsFile().toPath(), originalText);
                                Files.writeString(change.getFile().toPath(), finalText);
                            } else {
                                new DiffProblem(change.getFile(), diff(originalText, finalText), getLinkedApplyTaskPath().getOrNull()).make(reporter, getReportIssuesRootPath().getOrNull());
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
                    } catch (HandledException ignored) {}
                }
            });
        } finally {
            try {
                formatter.close(new SharedParams(reporter));
            } catch (HandledException ignored) {} finally {
                reporter.flush(this);
            }
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

    @Inject
    protected abstract Problems getProblems();

    @Inject
    protected abstract ObjectFactory getObjects();

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

    @ApiStatus.Internal
    public static abstract class LineBreakAugmentor extends FormattingAugmentor {
        @Override
        public FileAugmentor<?> augmentor() {
            return new FileAugmentor<Boolean>() {
                @Override
                public String augment(FileParams params, Consumer<Boolean> store) {
                    store.accept(params.text().contains("\r\n"));
                    return params.text().replace("\r\n", "\n");
                }

                @Override
                public String deAugment(FileParams params, Boolean crlf) {
                    var text = params.text().replace("\r\n", "\n");
                    if (crlf) {
                        text = text.replace("\n", "\r\n");
                    }
                    return text;
                }
            };
        }
    }

    protected void from(FormattingWorkflow workflow) {
        var wrapper = getObjects().domainObjectContainer(AugmentorStep.class).register(getPath(), step -> {
            step.getAugmentor().set(getObjects().domainObjectContainer(LineBreakAugmentor.class).register(getPath(), augmentor -> {
                augmentor.getSteps().addAllLater(workflow.getSteps().getElements());
            }));
            step.workflow(workflow);
        });

        this.getFormattingStep().set(wrapper);

        this.getFiles().from(workflow.getFiles());
        getReportIssuesRootPath().set(workflow.getReportIssuesRootPath());
    }
}
