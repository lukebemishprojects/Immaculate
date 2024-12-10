package dev.lukebemish.immaculate.wrapper.googlejavaformat;

import com.google.googlejavaformat.java.Main;
import dev.lukebemish.immaculate.wrapper.Wrapper;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class GoogleJavaFormatWrapper implements Wrapper {
    private final String[] args;
    public GoogleJavaFormatWrapper(String[] args) {
        this.args = args;
    }

    @Override
    public String format(String fileName, String text) {
        ByteArrayInputStream inStream = new ByteArrayInputStream(text.getBytes());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter outWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)));
        PrintWriter errWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8)));
        var formatter = new Main(outWriter, errWriter, inStream);
        String[] fullArgs = new String[args.length + 1];
        fullArgs[args.length] = "-";
        System.arraycopy(args, 0, fullArgs, 0, args.length);
        try {
            int ok = formatter.format(fullArgs);
            if (ok != 0) {
                throw new RuntimeException("Failed to format " + fileName + ", exit code: " + ok);
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            } else {
                throw new RuntimeException(e);
            }
        } finally {
            errWriter.flush();
            outWriter.flush();
        }
        return outputStream.toString(StandardCharsets.UTF_8);
    }
}
