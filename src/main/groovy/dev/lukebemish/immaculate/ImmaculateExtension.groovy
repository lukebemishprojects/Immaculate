package dev.lukebemish.immaculate

import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project

import javax.inject.Inject

@CompileStatic
abstract class ImmaculateExtension {
    abstract NamedDomainObjectContainer<FormattingWorkflow> getWorkflows()

    @Inject
    abstract protected Project getProject()
}
