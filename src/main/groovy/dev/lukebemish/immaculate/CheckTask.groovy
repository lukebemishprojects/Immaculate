package dev.lukebemish.immaculate

import groovy.transform.CompileStatic
import org.apache.commons.text.diff.CommandVisitor
import org.apache.commons.text.diff.StringsComparator
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

import java.nio.charset.StandardCharsets

@CacheableTask
@CompileStatic
abstract class CheckTask extends DefaultTask {
    @Nested
    abstract ListProperty<FormattingStep> getSteps()

    @Incremental
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    abstract ConfigurableFileCollection getFiles()
    
    @Input
    abstract Property<Boolean> getApplyFixes()
    
    @Internal
    abstract DirectoryProperty getOldCopyDirectory()

    @TaskAction
    void execute(InputChanges inputs) {
        if (getApplyFixes().get()) {
            getOldCopyDirectory().get().asFile.deleteDir()
        }
        inputs.getFileChanges(files).each { change ->
            if (change.changeType !== ChangeType.MODIFIED && change.fileType === FileType.FILE) {
                var originalLines = change.file.text.split(/((\r\n)|\r|\n)/, -1).toList()
                var finalLines = Collections.unmodifiableList(originalLines)
                steps.get().each { step ->
                    var lines = finalLines
                    List<String> newLines
                    try {
                        newLines = step.fix(lines)
                    } catch (e) {
                        throw new RuntimeException("Error checking file ${change.file.name} at step ${step.name}", e)
                    }
                    if (newLines !== null && lines != newLines) {
                        if (lines.any { it === null }) {
                            throw new RuntimeException("Attempted to correct to null line for file ${change.file.name} in step ${step.name}; we don't know what this means")
                        }
                        if (getApplyFixes().get()) {
                            finalLines = Collections.unmodifiableList(newLines)
                        } else {
                            throw new RuntimeException("File ${change.file.name} does not match formatting at step ${step.name}")
                        }
                    }
                }
                if (getApplyFixes().get() && originalLines != finalLines) {
                    // TODO: make line ending configurable (from .gitattributes, ideally)
                    var timestamp = new Date().format("yyyyMMddHHmmss")
                    var outFile = getOldCopyDirectory().file("${change.file.name}.${timestamp}.${change.file.text.md5()}")
                    outFile.get().asFile.parentFile.mkdirs()
                    outFile.get().asFile.write(change.file.text)
                    change.file.write(finalLines.join('\n'))
                }
            }
        }
    }

    CheckTask() {
        outputs.upToDateWhen { false }
        applyFixes.convention(false)
        oldCopyDirectory.convention(project.layout.buildDirectory.dir("immaculate/${this.name}"))
    }
}
