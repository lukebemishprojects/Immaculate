package dev.lukebemish.immaculate;

import java.io.File;

public record FileParams(File file, String fileName, String fileDisplayPath, String text, ProblemReportingHelper reporter) {}
