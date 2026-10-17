package dev.lukebemish.immaculate;

import org.gradle.api.file.Directory;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemReporter;
import org.jetbrains.annotations.Nullable;

import java.io.File;

class DiffProblem {
    private static final ProblemId DIFF =
        ProblemId.create("dev.lukebemish.immaculate.diff", "Non-matching output", CheckTask.PROBLEM_GROUP);

    private final File file;
    private final String diff;

    public DiffProblem(File file, String diff) {
        this.file = file;
        this.diff = diff;
    }

    public class DiffException extends RuntimeException {
        public DiffException(@Nullable Directory rootPath) {
            super("Issue formatting file " + (rootPath == null ? file.getName() : rootPath.getAsFile().toPath().relativize(file.toPath()).toString()) + ": output did not match original code");
        }
    }

    public void make(ProblemReporter reporter, @Nullable Directory rootPath) {
        throw reporter.throwing(
            new DiffException(rootPath),
            DIFF,
            spec -> {
                spec.details(diff);
                if (rootPath == null) {
                    spec.fileLocation(file.getPath());
                } else {
                    spec.fileLocation(rootPath.getAsFile().toPath().relativize(file.toPath()).toString());
                }
            }
        );
    }
}
