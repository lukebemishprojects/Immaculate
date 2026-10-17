package dev.lukebemish.immaculate;

public record FileParams(String fileName, String fileRelativePath, String text, ProblemReportingHelper reporter) {}
