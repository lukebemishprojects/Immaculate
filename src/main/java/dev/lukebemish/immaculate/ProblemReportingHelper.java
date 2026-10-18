package dev.lukebemish.immaculate;

import org.gradle.api.Action;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.Task;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.ProblemSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProblemReportingHelper {
    private final ProblemReporter problemReporter;
    private final List<RuntimeException> suppressed = Collections.synchronizedList(new ArrayList<>());

    ProblemReportingHelper(ProblemReporter problemReporter) {
        this.problemReporter = problemReporter;
    }

    public ProblemReporter reporter() {
        return problemReporter;
    }

    public void report(ProblemId id, Action<? super ProblemSpec> config) {
        problemReporter.report(id, config);
    }

    public RuntimeException error(Throwable e, ProblemId id, Action<? super ProblemSpec> config) {
        try {
            return problemReporter.throwing(e, id, config);
        } catch (RuntimeException re) {
            return re;
        }
    }

    public void throwingLater(Throwable e, ProblemId id, Action<? super ProblemSpec> config) {
        RuntimeException re;
        try {
            re = problemReporter.throwing(e, id, config);
        } catch (RuntimeException runtimeException) {
            re = runtimeException;
        }
        suppressed.add(re);
    }

    public RuntimeException throwing(Throwable e, ProblemId id, Action<? super ProblemSpec> config) {
        return problemReporter.throwing(e, id, config);
    }

    public void flush(Task task) {
        List<RuntimeException> other;
        synchronized (suppressed) {
            other = new ArrayList<>(suppressed);
            suppressed.clear();
        }
        if (other.isEmpty()) {
            return;
        }
        // TODO: figure out a better way of doing this
        // This works because it is the least irrelevant "throws multiple exceptions" type Gradle offers that is public API
        throw new ProjectConfigurationException("Multiple errors in '"+task.getPath()+"'", other);
    }
}
