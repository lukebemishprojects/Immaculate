package dev.lukebemish.immaculate;

import org.gradle.api.file.Directory;
import org.gradle.api.tasks.VerificationException;
import org.jspecify.annotations.Nullable;

import java.io.File;

class DiffProblems {
    public static class DiffException extends VerificationException {
        public DiffException(File file, @Nullable Directory rootPath) {
            super("Issue formatting file " + (rootPath == null ? file.getName() : rootPath.getAsFile().toPath().relativize(file.toPath()).toString()) + ": output did not match original code");
        }
    }

    public static RuntimeException make(File file, CheckTask.DiffData diff, @Nullable String linkedApplyPath, ProblemReportingHelper reporter, @Nullable Directory rootPath) {
        return reporter.error(
            new DiffException(file, rootPath),
            reporter.immaculateGroup().problemId("Immaculate output differs from file contents"),
            spec -> {
                spec.details(diff.pretty());
                String filePath;
                if (rootPath == null) {
                    filePath = file.getPath();
                } else {
                    filePath = rootPath.getAsFile().toPath().relativize(file.toPath()).toString();
                }
                for (var edit : diff.edits()) {
                    // These are actually line numbers
                    for (int i = edit.getBeginA(); i < edit.getBeginA() + edit.getLengthA(); i++) {
                        spec.lineInFileLocation(filePath, i);
                    }
                }
                if (linkedApplyPath != null) {
                    spec.solution("Run "+linkedApplyPath+" to apply formatting changes");
                }
            }
        );
    }
}
