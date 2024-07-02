package dev.lukebemish.immaculate;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;

import java.util.List;
import java.util.Objects;

public class ImmaculatePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        var extension = project.getExtensions().create("immaculate", ImmaculateExtension.class);

        var checkTask = project.getTasks().register("immaculateCheck", task -> {
            task.setGroup("verification");
            task.setDescription("Check code formatting with immaculate");
        });

        var applyTask = project.getTasks().register("immaculateApply", task -> {
            task.setGroup("verification");
            task.setDescription("Apply code formatting fixes with immaculate");
        });

        project.afterEvaluate(p -> {
            extension.getWorkflows().forEach(workflow -> {
                var workflowApply = project.getTasks().register(workflow.getName() + "ImmaculateApply", CheckTask.class, task -> {
                    task.getApplyFixes().set(true);
                    task.getSteps().set(workflow.getSteps());
                    task.getFiles().from(workflow.getFiles());
                    task.getToggleOff().set(workflow.getToggleOff());
                    task.getToggleOn().set(workflow.getToggleOn());
                });
                applyTask.configure(task -> {
                    task.dependsOn(workflowApply);
                });


                var workflowCheck = project.getTasks().register(workflow.getName() + "ImmaculateCheck", CheckTask.class, task -> {
                    task.getSteps().set(workflow.getSteps());
                    task.getFiles().from(workflow.getFiles());
                    task.mustRunAfter(workflowApply);
                    task.getToggleOff().set(workflow.getToggleOff());
                    task.getToggleOn().set(workflow.getToggleOn());
                });
                checkTask.configure(task -> {
                    task.dependsOn(workflowCheck);
                });
            });
        });
    }
}
