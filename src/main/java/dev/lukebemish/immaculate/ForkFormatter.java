package dev.lukebemish.immaculate;

import dev.lukebemish.forkedtaskexecutor.ForkedTaskExecutor;
import dev.lukebemish.forkedtaskexecutor.ForkedTaskExecutorSpec;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
        builder.addJvmOption("-cp");;
        builder.addJvmOption(spec.getClasspath().getAsPath());

        var executorSpec = builder.build();

        this.executor = new ForkedTaskExecutor(executorSpec);
    }

    @Override
    public void close() {
        this.executor.close();
    }

    @Override
    public String format(String fileName, String text) {
        try {
            var output = new ByteArrayOutputStream();
            try (var os = new DataOutputStream(output)) {
                os.writeUTF(fileName);
                os.writeUTF(text);
            }
            var bytes = executor.submit(output.toByteArray());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
