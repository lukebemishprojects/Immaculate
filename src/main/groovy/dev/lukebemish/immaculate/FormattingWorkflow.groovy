package dev.lukebemish.immaculate

import dev.lukebemish.immaculate.steps.GoogleJavaFormatStep
import dev.lukebemish.immaculate.steps.LinewiseStep
import dev.lukebemish.immaculate.steps.NoTabsStep
import dev.lukebemish.immaculate.steps.TrailingNewlineStep
import groovy.transform.CompileStatic
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSetContainer

import javax.inject.Inject
import java.util.function.UnaryOperator

@CompileStatic
abstract class FormattingWorkflow implements Named {
    abstract ExtensiblePolymorphicDomainObjectContainer<FormattingStep> getSteps()

    abstract ConfigurableFileCollection getFiles()

    @Inject
    protected abstract Project getProject()

    abstract Property<String> getToggleOff()
    abstract Property<String> getToggleOn()

    void java() {
        SourceSetContainer sourceSets = project.extensions.getByType(SourceSetContainer)
        ConfigurableFileCollection sourceDirs = project.files()
        sourceSets.configureEach {
            sourceDirs.from(it.allSource)
        }

        files.from(sourceDirs.filter { File it ->
            (it.name as String).endsWith('.java')
        })
    }

    void linewise(String name, UnaryOperator<String> customAction) {
        steps.register(name, LinewiseStep) {
            it.action.set(customAction)
        }
    }

    void trailingNewline() {
        steps.register('trailingNewline', TrailingNewlineStep)
    }

    void noTabs(int spacesPerTab) {
        steps.register('noTabs', NoTabsStep) {
            it.spacesPerTab.set(spacesPerTab)
        }
    }

    void noTabs() {
        noTabs(4)
    }

    void removeUnusedImports() {
        steps.register('removeUnusedImports', GoogleJavaFormatStep) {
            it.args.addAll('--fix-imports-only', '--skip-sorting-imports')
            it.defaultVersion()
        }
    }

    FormattingWorkflow() {
        for (Class<? extends FormattingStep> clazz : [
                LinewiseStep, TrailingNewlineStep, NoTabsStep
        ]) {
            registerStepType(clazz)
        }
        for (Class<? extends FormattingStep> clazz : [
                GoogleJavaFormatStep
        ]) {
            registerFormatterStepType(clazz)
        }
    }

    @Inject
    protected abstract ObjectFactory getObjects();

    private <T extends FormattingStep> void registerStepType(Class<T> clazz) {
        steps.registerFactory(clazz, { String name -> (T) objects.newInstance(clazz, name) } as NamedDomainObjectFactory<T>)
    }

    private <T extends FormattingStep> void registerFormatterStepType(Class<T> clazz) {
        steps.registerFactory(clazz, { String name -> (T) objects.newInstance(clazz, name, this.name) } as NamedDomainObjectFactory<T>)
    }
}
