package dev.lukebemish.immaculate.steps;

import dev.lukebemish.immaculate.FileAugmentor;
import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FileParams;
import dev.lukebemish.immaculate.FormattingAugmentor;
import dev.lukebemish.immaculate.FormattingStep;
import dev.lukebemish.immaculate.FormattingWorkflow;
import dev.lukebemish.immaculate.HandledException;
import dev.lukebemish.immaculate.SharedParams;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.VerificationException;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static dev.lukebemish.immaculate.CheckTask.PROBLEM_GROUP;

public abstract class AugmentorStep extends FormattingStep {
    private static final ProblemId FORMATTER_EXCEPTION =
        ProblemId.create("formatter-exception", "Exception thrown in formatter", PROBLEM_GROUP);
    private static final ProblemId AUGMENT_EXCEPTION =
        ProblemId.create("formatter-exception", "Exception thrown while applying augmentor", PROBLEM_GROUP);
    private static final ProblemId DEAUGMENT_EXCEPTION =
        ProblemId.create("formatter-exception", "Exception thrown while unapplying augmentor", PROBLEM_GROUP);
    private static final ProblemId FAILED_TO_CLOSE_FORMATTER =
        ProblemId.create("failed-to-close-formatter", "Failed to close formatter", PROBLEM_GROUP);
    private static final ProblemId FAILED_TO_CLOSE_AUGMENTOR =
        ProblemId.create("failed-to-close-augmentor", "Failed to close augmentor", PROBLEM_GROUP);

    @Nested
    public abstract Property<FormattingAugmentor> getAugmentor();

    private record NamedFormatter(String name, FileFormatter formatter) {}

    @Override
    public FileFormatter formatter() {
        var augmentor = getAugmentor().get();

        var impl = augmentor.augmentor();
        return formatterImpl(augmentor, impl);
    }

    private <T extends @Nullable Object> FileFormatter formatterImpl(FormattingAugmentor augmentor, FileAugmentor<T> impl) {
        List<NamedFormatter> formatters = augmentor.getSteps().stream().map(step -> new NamedFormatter(step.getName(), step.formatter())).toList();

        return new FileFormatter() {
            @Override
            public String format(FileParams params) {
                String text = params.text();
                var ref = new AtomicReference<T>();
                try {
                    text = impl.augment(new FileParams(params.file(), params.fileName(), params.fileDisplayPath(), text, params.reporter()), ref::set);
                } catch (HandledException ignored) {}  catch (Exception e) {
                    VerificationException ve;
                    if (e instanceof VerificationException verificationException) {
                        ve = verificationException;
                    } else {
                        ve = new VerificationException("Error in augmentor application", e);
                    }
                    params.reporter().throwingLater(
                        ve,
                        AUGMENT_EXCEPTION,
                        spec -> {
                            spec.contextualLabel("Error from augmentor '" + augmentor.getName() + "'");
                            spec.fileLocation(params.fileDisplayPath());
                        });
                    throw new HandledException();
                }

                for (var formatter : formatters) {
                    try {
                        text = formatter.formatter.format(new FileParams(params.file(), params.fileName(), params.fileDisplayPath(), text, params.reporter()));
                    } catch (HandledException ignored) {}  catch (Exception e) {
                        VerificationException ve;
                        if (e instanceof VerificationException verificationException) {
                            ve = verificationException;
                        } else {
                            ve = new VerificationException("Error in formatter step", e);
                        }
                        params.reporter().throwingLater(
                            ve,
                            FORMATTER_EXCEPTION,
                            spec -> {
                                spec.contextualLabel("Error from formatter '" + formatter.name() + "'");
                                spec.fileLocation(params.fileDisplayPath());
                            });
                        throw new HandledException();
                    }
                }

                try {
                    text = impl.deAugment(new FileParams(params.file(), params.fileName(), params.fileDisplayPath(), text, params.reporter()), ref.get());
                } catch (HandledException ignored) {} catch (Exception e) {
                    VerificationException ve;
                    if (e instanceof VerificationException verificationException) {
                        ve = verificationException;
                    } else {
                        ve = new VerificationException("Error in augmentor unapplication", e);
                    }
                    params.reporter().throwingLater(
                        ve,
                        DEAUGMENT_EXCEPTION,
                        spec -> {
                            spec.contextualLabel("Error from augmentor '" + augmentor.getName() + "'");
                            spec.fileLocation(params.fileDisplayPath());
                        });
                    throw new HandledException();
                }

                return text;
            }

            @Override
            public void close(SharedParams params) {
                FileFormatter.super.close(params);
                for (NamedFormatter formatter : formatters) {
                    try {
                        formatter.formatter.close(params);
                    } catch (HandledException ignored) {}  catch (Exception e) {
                        params.reporter().throwingLater(
                            e,
                            FAILED_TO_CLOSE_FORMATTER,
                            spec -> {
                                spec.contextualLabel("Error from formatter '" + formatter.name() + "'");
                            }
                        );
                    }
                }
                try {
                    impl.close(params);
                } catch (HandledException ignored) {}  catch (Exception e) {
                    params.reporter().throwingLater(
                        e,
                        FAILED_TO_CLOSE_AUGMENTOR,
                        spec -> {
                            spec.contextualLabel("Error from augmentor '" + augmentor.getName() + "'");
                        }
                    );
                }
            }
        };
    }

    @Override
    public void workflow(FormattingWorkflow workflow) {
        super.workflow(workflow);
        getAugmentor().finalizeValue();
        getAugmentor().get().workflow(workflow);
    }
}
