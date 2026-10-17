package dev.lukebemish.immaculate;

public interface FileFormatter {
    String format(FileParams params);

    default void close() {}
}
