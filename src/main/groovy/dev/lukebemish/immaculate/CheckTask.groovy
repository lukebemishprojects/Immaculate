package dev.lukebemish.immaculate

import groovy.transform.CompileStatic
import org.eclipse.jgit.diff.*
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

import java.nio.charset.StandardCharsets

@CacheableTask
@CompileStatic
abstract class CheckTask extends DefaultTask {
    @Nested
    abstract ListProperty<FormattingStep> getSteps()

    @Input
    abstract ListProperty<String> getStepOrder()

    @Incremental
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    abstract ConfigurableFileCollection getFiles()

    @Input
    abstract Property<Boolean> getApplyFixes()

    @Internal
    abstract DirectoryProperty getOldCopyDirectory()

    @Input
    @Optional
    abstract Property<String> getToggleOff()

    @Input
    @Optional
    abstract Property<String> getToggleOn()

    @TaskAction
    void execute(InputChanges inputs) {
        if (getApplyFixes().get()) {
            getOldCopyDirectory().get().asFile.deleteDir()
        }
        Map<String, FormattingStep> stepsMap = [:]
        getSteps().get().each { step ->
            stepsMap.put(step.name, step)
        }
        inputs.getFileChanges(files).each { change ->
            if (change.changeType !== ChangeType.MODIFIED && change.fileType === FileType.FILE) {
                String originalText = change.file.text
                String text = originalText

                // Grab toggles
                int index = 0
                List<String> parts = []
                List<String> excluded = []
                if (getToggleOff().isPresent() && getToggleOn().isPresent()) {
                    while (index < text.length()) {
                        int oldIndex = index
                        index = text.indexOf(getToggleOff().get(), index)
                        if (index != -1) {
                            int end = text.indexOf(getToggleOn().get(), index)
                            if (end != -1) {
                                int startIndex = index + getToggleOff().get().length()
                                int nextIndex = end + getToggleOn().get().length()
                                String suffix = ':' + excluded.size()
                                parts.add(text.substring(oldIndex, startIndex) + suffix)
                                excluded.add(text.substring(startIndex, nextIndex))
                                parts.add(text.substring(startIndex, nextIndex) + suffix)
                                index = nextIndex
                            } else {
                                throw new RuntimeException("Mismatched toggle in file ${change.file.name}")
                            }
                        } else {
                            parts.add(text.substring(oldIndex))
                            break
                        }
                    }
                    text = parts.join('')
                }


                var originalLines = text.split(/((\r\n)|\r|\n)/, -1).toList()
                boolean crlf = text.contains("\r\n")
                var finalLines = Collections.unmodifiableList(originalLines)
                stepOrder.get().each { stepName ->
                    var step = stepsMap[stepName]
                    var lines = finalLines
                    List<String> newLines
                    try {
                        newLines = step.fix(change.file.name, lines)
                    } catch (e) {
                        throw new RuntimeException("Error checking file ${change.file.name} at step ${step.name}", e)
                    }
                    if (newLines !== null && lines != newLines) {
                        if (lines.any { it === null }) {
                            throw new RuntimeException("Attempted to correct to null line for file ${change.file.name} in step ${step.name}; we don't know what this means")
                        }
                        finalLines = Collections.unmodifiableList(newLines)
                    }
                }
                String finalText = finalLines.join(crlf ? "\r\n" : '\n')

                // Replace toggles
                if (getToggleOff().isPresent() && getToggleOn().isPresent()) {
                    for (int i = 0; i < excluded.size(); i++) {
                        int start = finalText.indexOf(getToggleOff().get() + ':' + i)
                        if (start == -1) {
                            throw new RuntimeException("Could not recover toggle block in file ${change.file.name}")
                        }
                        int end = finalText.indexOf(getToggleOn().get() + ':' + i, start)
                        if (end == -1) {
                            throw new RuntimeException("Could not recover toggle block in file ${change.file.name}")
                        }
                        int startIndex = start + getToggleOff().get().length()+":$i".length()
                        int nextIndex = end + getToggleOn().get().length()+":$i".length()
                        String inner = finalText.substring(startIndex, end)
                        if (inner.contains(getToggleOff().get()) || inner.contains(getToggleOn().get())) {
                            throw new RuntimeException("Mismatched toggle in file ${change.file.name}")
                        }
                        finalText = finalText.substring(0, start) + getToggleOff().get() + excluded[i] + finalText.substring(nextIndex)
                    }
                }

                if (originalText != finalText) {
                    if (getApplyFixes().get()) {
                        var timestamp = new Date().format("yyyyMMddHHmmss")
                        var outFile = getOldCopyDirectory().file("${change.file.name}.${timestamp}.${change.file.text.md5()}")
                        outFile.get().asFile.parentFile.mkdirs()
                        outFile.get().asFile.write(originalText)
                        change.file.write(finalText)
                    } else {
                        throw new RuntimeException("File ${change.file.name} does not match formatting:\n${diff(originalText, finalText)}")
                    }
                }
            }
        }
    }

    private static final String diff(String oldString, String newString) {
        RawText oldText = new RawText(makeVisible(oldString).getBytes(StandardCharsets.UTF_8));
        RawText newText = new RawText(makeVisible(newString).getBytes(StandardCharsets.UTF_8));
        EditList edits = new EditList()
        edits.addAll(MyersDiff.INSTANCE.diff(
            RawTextComparator.DEFAULT,
            oldText,
            newText
        ))
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.format(edits, oldText, newText)
        }
        return out.toString(StandardCharsets.UTF_8.name())
    }

    private static final String makeVisible(String text) {
        return text.replace(' ', '\u00b7').replace('\t', '\\t').replace('\r', '')
    }

    CheckTask() {
        outputs.upToDateWhen { false }
        applyFixes.convention(false)
        oldCopyDirectory.convention(project.layout.buildDirectory.dir("immaculate/${this.name}"))
    }

    protected void from(FormattingWorkflow workflow) {
        this.getSteps().set(workflow.getSteps())
        this.getStepOrder().set(workflow.getStepOrder())
        this.getFiles().from(workflow.getFiles())
        this.getToggleOff().set(workflow.getToggleOff())
        this.getToggleOn().set(workflow.getToggleOn())
    }
}
