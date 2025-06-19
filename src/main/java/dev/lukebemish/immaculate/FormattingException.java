package dev.lukebemish.immaculate;

import org.jetbrains.annotations.Nullable;

import java.io.File;

public class FormattingException extends RuntimeException {
    private final File file;
    private final @Nullable String diff;

    public FormattingException(File file, String message, @Nullable String diff, @Nullable Throwable cause) {
        super(message, cause);
        this.file = file;
        this.diff = diff;
    }

    public FormattingException(File file, String message) {
        this(file, message, null, null);
    }

    public FormattingException(File file, String message, Throwable cause) {
        this(file, message, null, cause);
    }

    public FormattingException(File file, String message, String diff) {
        this(file, message, diff, null);
    }

    public String format(File root) {
        return "Issue formatting file " + (root != null ? root.toPath().relativize(file.toPath()) : file.getName()) + "\n" + super.getMessage() + (diff == null ? "" : "\n"+diff);
    }

    @Override
    public String getMessage() {
        return format(null);
    }
}
