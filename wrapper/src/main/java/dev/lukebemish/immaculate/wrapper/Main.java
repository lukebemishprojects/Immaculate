package dev.lukebemish.immaculate.wrapper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main implements AutoCloseable {
    private static final boolean STACKTRACE = !Boolean.getBoolean("dev.lukebemish.immaculate.wrapper.hidestacktrace");

    private final ServerSocket socket;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Wrapper wrapper;
    private Main(Wrapper wrapper) throws IOException {
        this.wrapper = wrapper;
        this.socket = new ServerSocket(0);
    }

    public static void main(String[] args) {
        try {
            Class<?> wrapperClass = Class.forName(args[0], false, Main.class.getClassLoader());
            if (!Wrapper.class.isAssignableFrom(wrapperClass)) {
                throw new IllegalArgumentException("Class " + args[0] + " does not implement Wrapper");
            }
            Constructor<?> constructor = wrapperClass.getConstructor(String[].class);
            String[] otherArgs = new String[args.length - 1];
            System.arraycopy(args, 1, otherArgs, 0, otherArgs.length);
            Wrapper wrapper = (Wrapper) constructor.newInstance((Object) otherArgs);
            try (Main runner = new Main(wrapper)) {
                runner.run();
            }
            System.exit(0);
        }catch (Throwable t) {
            logException(t);
            System.exit(1);
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
        executor.shutdownNow();
        try {
            executor.awaitTermination(4000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logException(e);
            throw new RuntimeException(e);
        }
    }

    private void run() throws IOException {
        // This tells the parent process what port we're listening on
        System.out.println(socket.getLocalPort());
        var socket = this.socket.accept();
        var input = new DataInputStream(socket.getInputStream());
        var os = new DataOutputStream(socket.getOutputStream());
        var output = new Output(os);
        while (true) {
            int id = input.readInt();
            if (id == -1) {
                break;
            }
            String fileName = new String(input.readNBytes(input.readInt()), StandardCharsets.UTF_8);
            String text = new String(input.readNBytes(input.readInt()), StandardCharsets.UTF_8);
            execute(id, fileName, text, output);
        }
    }

    private void execute(int id, String fileName, String text, Output output) {
        executor.submit(() -> {
            try {
                String result = wrapper.format(fileName, text);
                output.writeSuccess(id, result);
            } catch (Throwable t) {
                logException(t);
                try {
                    output.writeFailure(id);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                throw new RuntimeException(t);
            }
        });
    }

    private static void logException(Throwable t) {
        if (STACKTRACE) {
            t.printStackTrace(System.err);
        } else {
            System.err.println(t);
        }
    }

    private record Output(DataOutputStream output) {
        void writeFailure(int id) throws IOException {
            synchronized (this) {
                output.writeInt(id);
                output.writeBoolean(false);
                output.flush();
            }
        }

        void writeSuccess(int id, String result) throws IOException {
            synchronized (this) {
                output.writeInt(id);
                output.writeBoolean(true);
                byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
                output.writeInt(bytes.length);
                output.write(bytes);
                output.flush();
            }
        }
    }
}
