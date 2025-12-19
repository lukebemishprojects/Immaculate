package dev.lukebemish.immaculate.steps;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;

class DefaultVersions {
    static final String GOOGLE_JAVA_FORMAT;
    static final String PALANTIR_JAVA_FORMAT;
    static final String ECLIPSE_JDT;

    static {
        var props = new Properties();
        try (var stream = DefaultVersions.class.getResourceAsStream("/dev/lukebemish/immaculate/default-versions.properties")) {
            props.load(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        GOOGLE_JAVA_FORMAT = props.getProperty("google-java-format");
        PALANTIR_JAVA_FORMAT = props.getProperty("palantir-java-format");
        ECLIPSE_JDT = props.getProperty("eclipse-jdt");
    }
}
