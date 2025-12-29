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
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
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

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.StreamSupport;

public abstract class CheckTask extends DefaultTask {
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
    public abstract RegularFileProperty getReportIssuesRootPath();

    private record NamedFormatter(String name, FileFormatter formatter) {}

    private static final DateFormat OLD_FILE_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

    @TaskAction
    void execute(InputChanges inputs) throws IOException {
        if (getApplyFixes().get()) {
            FileUtils.deleteDirectory(getOldCopyDirectory().get().getAsFile());
        }
        List<NamedFormatter> formatters = getSteps().get().stream().map(step -> new NamedFormatter(step.getName(), step.formatter())).toList();
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
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
                                    throw new FormattingException(change.getFile(), "Mismatched toggle");
                                }
                            } else {
                                parts.add(text.substring(oldIndex));
                                break;
                            }
                        }
                        text = String.join("", parts);
                    }


                    boolean crlf = text.contains("\r\n");
                    final var finalText = new String[] {text};
                    formatters.forEach(formatter -> {
                        String newText;
                        try {
                            newText = formatter.formatter.format(change.getFile().getName(), finalText[0]);
                        } catch (Exception e) {
                            throw new FormattingException(change.getFile(), "Error at step "+formatter.name(), e);
                        }
                        if (newText != null && !finalText[0].equals(newText)) {
                            finalText[0] = newText;
                        }
                    });
                    finalText[0] = finalText[0].replace("\r\n", "\n");
                    if (crlf) {
                        finalText[0] = finalText[0].replace("\n", "\r\n");
                    }

                    // Replace toggles
                    if (getToggleOff().isPresent() && getToggleOn().isPresent()) {
                        for (int i = 0; i < excluded.size(); i++) {
                            int start = finalText[0].indexOf(getToggleOff().get() + ':' + i);
                            if (start == -1) {
                                throw new FormattingException(change.getFile(), "Could not recover toggle block");
                            }
                            int end = finalText[0].indexOf(getToggleOn().get() + ':' + i, start);
                            if (end == -1) {
                                throw new FormattingException(change.getFile(), "Could not recover toggle block");
                            }
                            int startIndex = start + getToggleOff().get().length() + (":"+i).length();
                            int nextIndex = end + getToggleOn().get().length() + (":"+i).length();
                            String inner = finalText[0].substring(startIndex, end);
                            if (inner.contains(getToggleOff().get()) || inner.contains(getToggleOn().get())) {
                                throw new FormattingException(change.getFile(), "Mismatched toggle");
                            }
                            finalText[0] = finalText[0].substring(0, start) + getToggleOff().get() + excluded.get(i) + finalText[0].substring(nextIndex);
                        }
                    }

                    if (!originalText.equals(finalText[0])) {
                        if (getApplyFixes().get()) {
                            var timestamp = OLD_FILE_DATE_FORMAT.format(new Date());
                            var outFile = getOldCopyDirectory().file(change.getFile().getName()+"."+timestamp+DigestUtils.md5Hex(originalText));
                            Files.createDirectories(outFile.get().getAsFile().toPath().getParent());
                            Files.writeString(outFile.get().getAsFile().toPath(), originalText);
                            Files.writeString(change.getFile().toPath(), finalText[0]);
                        } else {
                            throw new FormattingException(change.getFile(), "File does not match formatting", diff(originalText, finalText[0]));
                        }
                    }
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            }
        });
        for (NamedFormatter formatter : formatters) {
            try {
                formatter.formatter.close();
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        if (!exceptions.isEmpty()) {
            RuntimeException exception;
            if (exceptions.size() > 5 && getTruncateExceptions().orElse(getLoggingConfiguration().getShowStacktrace() == ShowStacktrace.INTERNAL_EXCEPTIONS).get()) {
                exception = new RuntimeException("Exceptions occurred during formatting; see log for details (first 5 shown; others truncated...");
                for (int i = 0; i < 5; i++) {
                    printFormattingException(exceptions.get(i));
                }
                exceptions.forEach(exception::addSuppressed);
            } else {
                exception = new RuntimeException("Exceptions occurred during formatting; see log for details");
                exceptions.forEach(this::printFormattingException);
                exceptions.forEach(exception::addSuppressed);
            }
            throw exception;
        }
    }

    @Inject
    protected abstract LoggingConfiguration getLoggingConfiguration();

    private void printFormattingException(Exception e) {
        if (e instanceof FormattingException formattingException) {
            System.out.println(formattingException.format(getReportIssuesRootPath().getAsFile().getOrNull()));
        } else {
            System.err.println(e);
        }
    }

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
