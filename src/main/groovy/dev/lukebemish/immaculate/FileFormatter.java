package dev.lukebemish.immaculate;

public interface FileFormatter {
    String format(String fileName, String text);

    default void close() {}
}
