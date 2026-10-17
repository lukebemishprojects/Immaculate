package dev.lukebemish.immaculate;

import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public interface FileAugmentor<T extends @Nullable Object> {
    String augment(FileParams params, Consumer<T> store);

    String deAugment(FileParams params, T value);

    default void close(SharedParams params) {}
}
