package dev.lukebemish.immaculate;

import org.gradle.api.file.Directory;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.tasks.VerificationException;
import org.jetbrains.annotations.Nullable;

import java.io.File;

class DiffProblem {
    private static final ProblemId DIFF =
        ProblemId.create("dev.lukebemish.immaculate.diff", "Non-matching output", CheckTask.PROBLEM_GROUP);

    private final File file;
    private final CheckTask.DiffData diff;

    public DiffProblem(File file, CheckTask.DiffData diff) {
        this.file = file;
        this.diff = diff;
    }

    public class DiffException extends VerificationException {
        public DiffException(@Nullable Directory rootPath) {
            super("Issue formatting file " + (rootPath == null ? file.getName() : rootPath.getAsFile().toPath().relativize(file.toPath()).toString()) + ": output did not match original code");
        }
    }

    public void make(BetterProblemReporter reporter, @Nullable Directory rootPath) {
        reporter.throwingLater(
            new DiffException(rootPath),
            DIFF,
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
            }
        );
    }
}
