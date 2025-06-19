package dev.lukebemish.immaculate;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class ImmaculateExtension {
    public abstract NamedDomainObjectContainer<FormattingWorkflow> getWorkflows();

    @Inject
    protected abstract Project getProject();

    public abstract Property<Boolean> getTruncateExceptions();
    public abstract RegularFileProperty getReportIssuesRootPath();

    @Inject
    public ImmaculateExtension() {
        getTruncateExceptions().convention(true);
        getWorkflows().configureEach(workflow -> {
            workflow.getTruncateExceptions().convention(getTruncateExceptions());
            workflow.getReportIssuesRootPath().convention(getReportIssuesRootPath());
        });
    }
}
