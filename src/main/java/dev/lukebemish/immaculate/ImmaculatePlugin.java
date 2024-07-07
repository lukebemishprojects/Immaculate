package dev.lukebemish.immaculate;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;

public class ImmaculatePlugin implements Plugin<Project> {
    public static final String PLUGIN_VERSION = ImmaculatePlugin.class.getPackage().getImplementationVersion();

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaBasePlugin.class);
        var extension = project.getExtensions().create("immaculate", ImmaculateExtension.class);

        var checkTask = project.getTasks().register("immaculateCheck", task -> {
            task.setGroup("verification");
            task.setDescription("Check code formatting with immaculate");
        });

        var applyTask = project.getTasks().register("immaculateApply", task -> {
            task.setGroup("verification");
            task.setDescription("Apply code formatting fixes with immaculate");
        });

        project.getTasks().named("check", task -> {
            task.dependsOn(checkTask);
        });

        project.afterEvaluate(p -> {
            extension.getWorkflows().forEach(workflow -> {
                var workflowApply = project.getTasks().register(workflow.getName() + "ImmaculateApply", CheckTask.class, task -> {
                    task.getApplyFixes().set(true);
                    task.from(workflow);
                });
                applyTask.configure(task -> {
                    task.dependsOn(workflowApply);
                });


                var workflowCheck = project.getTasks().register(workflow.getName() + "ImmaculateCheck", CheckTask.class, task -> {
                    task.from(workflow);
                    task.mustRunAfter(workflowApply);
                });
                checkTask.configure(task -> {
                    task.dependsOn(workflowCheck);
                });
            });
        });
    }
}
