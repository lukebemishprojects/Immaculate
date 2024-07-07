package dev.lukebemish.immaculate;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;

import javax.inject.Inject;

public abstract class ImmaculateExtension {
    public abstract NamedDomainObjectContainer<FormattingWorkflow> getWorkflows();

    @Inject
    protected abstract Project getProject();

    @Inject
    public ImmaculateExtension() {}
}
