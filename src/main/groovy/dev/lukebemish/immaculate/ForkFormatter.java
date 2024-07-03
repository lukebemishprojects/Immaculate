package dev.lukebemish.immaculate;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class ForkFormatter implements FileFormatter {
    private final Process process;
    private final Socket socket;
    private final ResultListener listener;

    public ForkFormatter(ForkFormatterSpec spec) {
        var builder = new ProcessBuilder();
        builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        builder.redirectError(ProcessBuilder.Redirect.PIPE);
        builder.redirectInput(ProcessBuilder.Redirect.PIPE);
        List<String> args = new ArrayList<>();
        args.add(spec.getJavaLauncher().get().getExecutablePath().getAsFile().toString());
        args.addAll(spec.getJvmArgs().get());
        args.addAll(List.of(
            "-cp",
            spec.getClasspath().getAsPath(),
            spec.getWrapperMainClass().get(),
            spec.getWrapperClass().get()
        ));
        args.addAll(spec.getProgramArgs().get());
        builder.command(args);
        try {
            this.process = builder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        CompletableFuture<String> socketPort = new CompletableFuture<>();
        var thread = new StreamWrapper(process.getInputStream(), socketPort);
        new Thread(() -> {
            try {
                InputStreamReader reader = new InputStreamReader(process.getErrorStream());
                BufferedReader bufferedReader = new BufferedReader(reader);
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    System.err.println(line);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).start();
        thread.start();
        try {
            String socketPortString = socketPort.get(4000, TimeUnit.MILLISECONDS);
            int port = Integer.parseInt(socketPortString);
            this.socket = new Socket(InetAddress.getLoopbackAddress(), port);

            this.listener = new ResultListener(socket);
            this.listener.start();
        } catch (InterruptedException | ExecutionException | TimeoutException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class StreamWrapper extends Thread {
        private final InputStream stream;
        private final CompletableFuture<String> socketPort;

        private StreamWrapper(InputStream stream, CompletableFuture<String> socketPort) {
            this.stream = stream;
            this.socketPort = socketPort;
            this.setUncaughtExceptionHandler((t, e) -> {
                socketPort.completeExceptionally(e);
                StreamWrapper.this.getThreadGroup().uncaughtException(t, e);
            });
        }

        @Override
        public void run() {
            try {
                var reader = new BufferedReader(new InputStreamReader(stream));
                socketPort.complete(reader.readLine());
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    private static final class ResultListener extends Thread {
        private final Map<Integer, CompletableFuture<String>> results = new ConcurrentHashMap<>();
        private final Socket socket;
        private final DataOutputStream output;

        private ResultListener(Socket socket) throws IOException {
            this.socket = socket;
            output = new DataOutputStream(socket.getOutputStream());
            this.setUncaughtExceptionHandler((t, e) -> {
                try {
                    shutdown(e);
                } catch (IOException ex) {
                    var exception = new UncheckedIOException(ex);
                    exception.addSuppressed(e);
                    ResultListener.this.getThreadGroup().uncaughtException(t, exception);
                }
                ResultListener.this.getThreadGroup().uncaughtException(t, e);
            });
        }

        public synchronized CompletableFuture<String> submit(int id, String fileName, String text) throws IOException {
            if (closed) {
                throw new IOException("Listener is closed");
            }
            var out = results.computeIfAbsent(id, i -> new CompletableFuture<>());
            output.writeInt(id);
            byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
            output.writeInt(fileNameBytes.length);
            output.write(fileNameBytes);
            output.writeInt(textBytes.length);
            output.write(textBytes);
            output.flush();
            return out;
        }

        private volatile boolean closed = false;

        private void beginClose(Throwable e) throws IOException {
            if (this.closed) return;
            this.closed = true;
            for (var future : results.values()) {
                future.completeExceptionally(e);
            }
            results.clear();

            socket.shutdownInput();
        }

        private void finishClose() throws IOException {
            output.writeInt(-1);
            socket.close();
        }

        public void shutdown() throws IOException {
            shutdown(new IOException("Execution was interrupted"));
        }

        private void shutdown(Throwable t) throws IOException {
            this.beginClose(t);
            try {
                this.join();
            } catch (InterruptedException e) {
                // continue, it's fine
            }
            this.finishClose();
        }

        @Override
        public void run() {
            try {
                if (!closed) {
                    var input = new DataInputStream(socket.getInputStream());
                    while (!closed) {
                        int id = input.readInt();
                        boolean success = input.readBoolean();
                        if (success) {
                            int length = input.readInt();
                            byte[] bytes = input.readNBytes(length);
                            String result = new String(bytes, StandardCharsets.UTF_8);
                            var future = results.remove(id);
                            if (future != null) {
                                future.complete(result);
                            }
                        } else {
                            var future = results.remove(id);
                            if (future != null) {
                                var exception = new RuntimeException("Process failed");
                                future.completeExceptionally(exception);
                            }
                        }
                    }
                }
            } catch (EOFException ignored) {

            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public synchronized void close() {
        List<Exception> suppressed = new ArrayList<>();
        if (listener != null) {
            try {
                listener.shutdown();
            } catch (Exception e) {
                suppressed.add(e);
            }
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception e) {
                suppressed.add(e);
            }
        }
        if (process != null) {
            try {
                process.destroy();
                process.waitFor();
            } catch (Exception e) {
                suppressed.add(e);
            }
        }
        if (!suppressed.isEmpty()) {
            var exception = new IOException("Failed to close resources");
            suppressed.forEach(exception::addSuppressed);
            throw new UncheckedIOException(exception);
        }
    }

    private final AtomicInteger id = new AtomicInteger();

    @Override
    public String format(String fileName, String text) {
        var nextId = id.getAndIncrement();
        try {
            return listener.submit(nextId, fileName, text).get();
        } catch (IOException | ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
