package dev.lukebemish.immaculate

import groovy.transform.CompileStatic
import org.gradle.api.Named

@CompileStatic
abstract class FormattingStep implements Named {
    abstract List<String> fix(List<String> lines)
}
