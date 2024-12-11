package dev.lukebemish.immaculate.wrapper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.net.Socket;
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
        // Communication back to the parent is done through this handle, which ensures synchronization on the output stream.
        var socketHandle = new SocketHandle(socket);
        while (true) {
            int id = socketHandle.readId();
            if (id == -1) {
                // We have been sent a signal to gracefully shutdown, so we stop processing new submissions
                break;
            }
            String fileName = socketHandle.readUTF();
            String text = socketHandle.readUTF();
            // Submissions to the child process take the format ID, file name, file contents
            execute(id, fileName, text, socketHandle);
        }
    }

    private void execute(int id, String fileName, String text, SocketHandle socketHandle) {
        executor.submit(() -> {
            try {
                String result = wrapper.format(fileName, text);
                socketHandle.writeSuccess(id, result);
            } catch (Throwable t) {
                logException(t);
                try {
                    socketHandle.writeFailure(id);
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

    private static final class SocketHandle {
        private final DataOutputStream output;
        private final DataInputStream input;

        private SocketHandle(Socket socket) throws IOException {
            this.output = new DataOutputStream(socket.getOutputStream());
            this.input = new DataInputStream(socket.getInputStream());
        }

        synchronized void writeFailure(int id) throws IOException {
            output.writeInt(id);
            output.writeBoolean(false);
            output.flush();
        }

        synchronized void writeSuccess(int id, String result) throws IOException {
            output.writeInt(id);
            output.writeBoolean(true);
            // We do not use writeUTF here as it is limited to strings with length less than 65535 bytes
            var resultBytes = result.getBytes(StandardCharsets.UTF_8);
            output.writeInt(resultBytes.length);
            output.write(resultBytes);
            output.flush();
        }

        int readId() throws IOException {
            return input.readInt();
        }

        String readUTF() throws IOException {
            // We do not use readUTF here as it is limited to strings with length less than 65535 bytes
            int length = input.readInt();
            var bytes = input.readNBytes(length);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
