package dev.lukebemish.immaculate;

import java.util.function.Consumer;

public interface FileAugmentor<T> {
    String augment(FileParams params, Consumer<T> store);

    String deAugment(FileParams params, T value);

    default void close() {}
}
