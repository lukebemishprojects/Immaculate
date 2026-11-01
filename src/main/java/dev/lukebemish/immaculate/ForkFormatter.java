package dev.lukebemish.immaculate;

import dev.lukebemish.forkedtaskexecutor.ForkedTaskExecutor;
import dev.lukebemish.forkedtaskexecutor.ForkedTaskExecutorSpec;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ForkFormatter implements FileFormatter {
    private final ForkedTaskExecutor executor;

    public ForkFormatter(ForkFormatterSpec spec) {
        var builder = ForkedTaskExecutorSpec.builder();
        builder.javaExecutable(spec.getJavaLauncher().get().getExecutablePath().getAsFile().toPath());
        builder.hideStacktrace(spec.getHideStacktrace().get());
        for (String arg : spec.getProgramArgs().get()) {
            builder.addProgramOption(arg);
        }
        for (String arg : spec.getJvmArgs().get()) {
            builder.addJvmOption(arg);
        }
        builder.taskClass(spec.getWrapperClass().get());
        builder.addJvmOption("-cp");
        builder.addJvmOption(spec.getClasspath().getAsPath());

        var executorSpec = builder.build();

        this.executor = new ForkedTaskExecutor(executorSpec);
    }

    @Override
    public void close(SharedParams params) {
        this.executor.close();
    }

    @Override
    public String format(FileParams params) {
        try {
            var output = new ByteArrayOutputStream();
            try (var os = new DataOutputStream(output)) {
                os.writeUTF(params.fileName());
                var encodedText = params.text().getBytes(StandardCharsets.UTF_8);
                os.writeInt(encodedText.length);
                os.write(encodedText);
            }
            var bytes = executor.submit(output.toByteArray());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
