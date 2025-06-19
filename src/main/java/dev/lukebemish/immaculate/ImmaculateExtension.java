package dev.lukebemish.immaculate;

import dev.lukebemish.immaculate.steps.WrapperFormattingStep;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import javax.inject.Inject;

public abstract class ImmaculateExtension {
    public abstract NamedDomainObjectContainer<FormattingWorkflow> getWorkflows();

    public abstract Property<Boolean> getTruncateExceptions();
    public abstract RegularFileProperty getReportIssuesRootPath();
    public abstract Property<JavaLauncher> getDefaultJavaLauncher();

    @Inject
    public ImmaculateExtension(Project project, JavaToolchainService javaToolchainService) {
        getTruncateExceptions().convention(true);
        getWorkflows().configureEach(workflow -> {
            workflow.getTruncateExceptions().convention(getTruncateExceptions());
            workflow.getReportIssuesRootPath().convention(getReportIssuesRootPath());
        });
        getDefaultJavaLauncher().convention(project.provider(() -> {
            var java = project.getExtensions().findByType(JavaPluginExtension.class);
            if (java != null) {
                return java.getToolchain();
            }
            return null;
        }).flatMap(javaToolchainService::launcherFor).orElse(javaToolchainService.launcherFor(spec -> {})));
        getWorkflows().configureEach(workflow -> {
            workflow.getSteps().configureEach(step -> {
                if (step instanceof WrapperFormattingStep wrapperFormattingStep) {
                    wrapperFormattingStep.getJavaLauncher().convention(getDefaultJavaLauncher());
                }
            });
        });
    }
}
