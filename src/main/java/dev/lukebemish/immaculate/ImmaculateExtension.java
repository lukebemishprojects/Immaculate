package dev.lukebemish.immaculate;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import javax.inject.Inject;

public abstract class ImmaculateExtension {
    public abstract NamedDomainObjectContainer<FormattingWorkflow> getWorkflows();

    public abstract DirectoryProperty getReportIssuesRootPath();
    public abstract Property<JavaLauncher> getDefaultJavaLauncher();

    @Inject
    public ImmaculateExtension(Project project, JavaToolchainService javaToolchainService) {
        getWorkflows().configureEach(workflow -> {
            workflow.getReportIssuesRootPath().convention(getReportIssuesRootPath());
        });
        getDefaultJavaLauncher().convention(project.provider(() -> {
            var java = project.getExtensions().findByType(JavaPluginExtension.class);
            if (java != null) {
                return java.getToolchain();
            }
            return null;
        }).flatMap(javaToolchainService::launcherFor).orElse(javaToolchainService.launcherFor(spec -> {})));

        getReportIssuesRootPath().convention(project.getLayout().getProjectDirectory());
    }
}
