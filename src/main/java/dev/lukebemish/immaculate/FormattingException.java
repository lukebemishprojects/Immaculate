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
        return format(root, false);
    }

    private String format(File root, boolean singleLine) {
        return "Issue formatting file " + (root != null ? root.toPath().relativize(file.toPath()) : file.getName()) + (singleLine ? ": " : "\n") + calculateDirectMessage() + (diff == null || singleLine ? "" : "\n"+diff);
    }

    private String calculateDirectMessage() {
        var message = super.getMessage();
        Throwable throwable = this;
        while ((throwable = throwable.getCause()) != null) {
            if (throwable.getMessage() != null && !throwable.getMessage().isEmpty()) {
                return message + ": " + throwable.getMessage();
            }
        }
        return message;
    }

    @Override
    public String getMessage() {
        return format(null, true);
    }
}
