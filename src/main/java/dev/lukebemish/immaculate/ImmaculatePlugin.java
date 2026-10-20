package dev.lukebemish.immaculate;

import dev.lukebemish.immaculate.steps.AugmentorStep;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaBasePlugin;

import javax.inject.Inject;

public abstract class ImmaculatePlugin implements Plugin<Project> {
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
                var wrapper = getObjects().domainObjectContainer(AugmentorStep.class).register(workflow.getName()+"Wrapper", step -> {
                    step.getAugmentor().set(getObjects().domainObjectContainer(CheckTask.LineBreakAugmentor.class).register(workflow.getName()+"Wrapper", augmentor -> {
                        augmentor.getSteps().addAllLater(workflow.getSteps().getElements());
                    }));
                    step.workflow(workflow);
                });

                var workflowApply = project.getTasks().register(workflow.getName() + "ImmaculateApply", CheckTask.class, task -> {
                    task.getFormattingStep().set(wrapper);
                    task.getApplyFixes().set(true);
                    task.from(workflow);
                });
                applyTask.configure(task -> {
                    task.dependsOn(workflowApply);
                });


                var workflowCheck = project.getTasks().register(workflow.getName() + "ImmaculateCheck", CheckTask.class, task -> {
                    task.getFormattingStep().set(wrapper);
                    task.from(workflow);
                    task.mustRunAfter(workflowApply);
                    task.getLinkedApplyTaskPath().set(workflowApply.get().getPath());
                });
                checkTask.configure(task -> {
                    task.dependsOn(workflowCheck);
                });
            });
        });
    }

    @Inject
    protected abstract ObjectFactory getObjects();
}
