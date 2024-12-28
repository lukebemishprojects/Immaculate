package dev.lukebemish.immaculate.wrapper.palantirjavaformat;

import com.palantir.javaformat.java.Main;
import dev.lukebemish.immaculate.wrapper.Wrapper;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class PalantirJavaFormatWrapper implements Wrapper {

    private final String[] args;

    public PalantirJavaFormatWrapper(final String[] args) {
        this.args = args;
    }

    @Override
    public String format(final String fileName, final String text) {
        final ByteArrayInputStream inStream = new ByteArrayInputStream(text.getBytes());
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final PrintWriter outWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)));
        final PrintWriter errWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8)));
        final var formatter = new Main(outWriter, errWriter, inStream);
        final String[] fullArgs = new String[this.args.length + 1];
        fullArgs[this.args.length] = "-";
        System.arraycopy(this.args, 0, fullArgs, 0, this.args.length);
        try {
            final int ok = formatter.format(fullArgs);
            if (ok != 0) {
                throw new RuntimeException("Failed to format " + fileName + ", exit code: " + ok);
            }
        } catch (final Exception e) {
            if (e instanceof final RuntimeException runtimeException) {
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
