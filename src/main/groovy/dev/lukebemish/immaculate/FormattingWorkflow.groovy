package dev.lukebemish.immaculate

import dev.lukebemish.immaculate.steps.CustomStep
import dev.lukebemish.immaculate.steps.EclipseJdtFormatStep
import dev.lukebemish.immaculate.steps.GoogleJavaFormatStep
import dev.lukebemish.immaculate.steps.LinewiseStep
import groovy.transform.CompileStatic
import org.gradle.api.*
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSetContainer

import javax.inject.Inject
import java.util.function.UnaryOperator

@CompileStatic
abstract class FormattingWorkflow implements Named {
    protected NamedDomainObjectList<FormattingStep> getSteps() {
        return this.steps
    }

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
        step(name, LinewiseStep) {
            it.action.set(customAction)
        }
    }

    void trailingNewline() {
        custom('trailingNewline') {
            if (!it.endsWith("\n") && !it.endsWith("\r\n")) {
                return it + "\n"
            }
            return it
        }
    }

    void noTabs(int spacesPerTab) {
        linewise('noTabs') {
            StringBuilder sb = new StringBuilder()
            for (int i = 0; i < it.length(); i++) {
                char c = it.charAt(i)
                if (c == '\t' as char) {
                    sb.append(' '.repeat(spacesPerTab))
                } else if (c == ' ' as char) {
                    sb.append(c)
                } else {
                    sb.append(it.substring(i))
                    break
                }
            }
        }
    }

    void noTabs() {
        noTabs(4)
    }

    void googleRemoveUnusedImports() {
        step('googleRemoveUnusedImports', GoogleJavaFormatStep) {
            it.args.addAll('--fix-imports-only', '--skip-sorting-imports')
        }
    }

    void googleSortImports() {
        step('googleSortImports', GoogleJavaFormatStep) {
            it.args.addAll('--fix-imports-only', '--skip-removing-unused-imports')
        }
    }

    void googleFixImports() {
        step('googleFixImports', GoogleJavaFormatStep) {
            it.args.addAll('--fix-imports-only')
        }
    }

    void google() {
        step('google', GoogleJavaFormatStep)
    }

    void google(Action<GoogleJavaFormatStep> action) {
        step('google', GoogleJavaFormatStep) {
            action.execute(it)
        }
    }

    void eclipse(Action<EclipseJdtFormatStep> action) {
        step('eclipse', EclipseJdtFormatStep) {
            action.execute(it)
        }
    }

    void noTrailingSpaces() {
        linewise('noTrailingSpaces') { it.stripTrailing() }
    }

    void custom(String name, UnaryOperator<String> customAction) {
        step(name, CustomStep) {
            it.action.set(customAction)
        }
    }

    <T extends FormattingStep> void step(String name, Class<T> type, Action<? super T> action) {
        steps.addLater(project.provider {
            T step = createStep(type, name)
            // Ugh, groovy 3 type checking
            (action as Action).execute(step)
            return step
        })
    }

    <T extends FormattingStep> void step(String name, Class<T> type) {
        step(name, type) {}
    }

    private final Map<Class<? extends FormattingStep>, NamedDomainObjectFactory<? extends FormattingStep>> factories = [:]

    FormattingWorkflow() {
        for (Class<? extends FormattingStep> clazz : [
            LinewiseStep, CustomStep
        ]) {
            registerStepType(clazz)
        }
        for (Class<? extends FormattingStep> clazz : [
            GoogleJavaFormatStep, EclipseJdtFormatStep
        ]) {
            registerFormatterStepType(clazz)
        }
    }

    private <T extends FormattingStep> T createStep(Class<T> clazz, String name) {
        NamedDomainObjectFactory<T> factory = factories.get(clazz) as NamedDomainObjectFactory<T>
        if (factory == null) {
            throw new IllegalArgumentException("No factory for step type $clazz")
        }
        return factory.create(name)
    }

    @Inject
    protected abstract ObjectFactory getObjects();

    private final NamedDomainObjectList<FormattingStep> steps = getObjects().namedDomainObjectList(FormattingStep)

    private <T extends FormattingStep> void registerStepType(Class<T> clazz) {
        factories.put(clazz, { String name -> (T) objects.newInstance(clazz, name) } as NamedDomainObjectFactory<T>)
    }

    private <T extends FormattingStep> void registerFormatterStepType(Class<T> clazz) {
        factories.put(clazz, { String name -> (T) objects.newInstance(clazz, name, this.name) } as NamedDomainObjectFactory<T>)
    }
}
